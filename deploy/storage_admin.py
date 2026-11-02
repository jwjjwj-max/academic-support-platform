#!/usr/bin/env python3
"""Private LHCOS migration and recoverable backups. Never prints credentials or SDK errors."""
import logging
logging.disable(logging.CRITICAL)
import argparse, datetime as dt, gzip, hashlib, io, json, os, pathlib, re, secrets, subprocess, sys, time, uuid
from qcloud_cos import CosConfig, CosS3Client
from qcloud_cos.cos_exception import CosServiceError

def config(path):
    values={}
    for line in pathlib.Path(path).read_text().splitlines():
        if line and not line.startswith('#') and '=' in line:
            k,v=line.split('=',1); values[k]=v.strip().strip('"').strip("'")
    return values

def digest_stream(stream):
    h=hashlib.sha256()
    for block in iter(lambda:stream.read(1024*1024),b''): h.update(block)
    return h.hexdigest()

def sha(path):
    with path.open('rb') as stream:return digest_stream(stream)

class Store:
    def __init__(self,path):
        c=config(path); self.bucket=c['COS_BUCKET']
        self.client=CosS3Client(CosConfig(Region=c['COS_REGION'],SecretId=c['COS_SECRET_ID'],SecretKey=c['COS_SECRET_KEY'],Scheme='https'))
    def hash(self,key):
        response=self.client.get_object(Bucket=self.bucket,Key=key)
        stream=response['Body'].get_raw_stream()
        try:return digest_stream(stream)
        finally:stream.close()
    def exists(self,key):
        try:self.client.head_object(Bucket=self.bucket,Key=key);return True
        except CosServiceError as e:
            if e.get_status_code()==404:return False
            raise
    def put(self,key,path):
        # LHCOS: do not pass StorageClass=STANDARD.
        with path.open('rb') as stream:self.client.put_object(Bucket=self.bucket,Key=key,Body=stream)
        if self.hash(key)!=sha(path):raise RuntimeError('Uploaded object hash mismatch')
    def json(self,key,value):
        self.client.put_object(Bucket=self.bucket,Key=key,Body=json.dumps(value).encode(),ContentType='application/json')
    def list(self,prefix):
        marker=''
        while True:
            result=self.client.list_objects(Bucket=self.bucket,Prefix=prefix,Marker=marker,MaxKeys=1000)
            yield from result.get('Contents',[])
            if str(result.get('IsTruncated','false')).lower()!='true':break
            marker=result['NextMarker']

def migrate(store,root,manifest_path):
    root=pathlib.Path(root).resolve(); records=[]
    for path in sorted(root.rglob('*')):
        if not path.is_file():continue
        if path.is_symlink():raise RuntimeError('Symlink in migration source')
        key=path.relative_to(root).as_posix()
        if not re.fullmatch(r'(photos|grades|moral)/[A-Za-z0-9_-]+\.[A-Za-z0-9]+',key):raise RuntimeError('Unexpected migration key')
        expected=sha(path)
        if store.exists(key):
            if store.hash(key)!=expected:raise RuntimeError('Existing object differs; refusing overwrite')
        else:store.put(key,path)
        records.append({'key':key,'size':path.stat().st_size,'sha256':expected})
    pathlib.Path(manifest_path).write_text(json.dumps(records,indent=2))
    print(json.dumps({'verified_files':len(records),'bytes':sum(r['size'] for r in records)}))

def sql(container,query):
    command=['docker','exec',container,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B -e "$1"','sh',query]
    return subprocess.run(command,capture_output=True,check=True,text=True).stdout.strip()

def restore_test(path):
    name='academic-restore-check-'+uuid.uuid4().hex[:10]
    env=dict(os.environ,MYSQL_ROOT_PASSWORD=secrets.token_urlsafe(24))
    started=False
    try:
        subprocess.run(['docker','run','-d','--name',name,'--network','none','--env','MYSQL_ROOT_PASSWORD','--tmpfs','/var/lib/mysql:rw,size=1g','mysql:8.0'],env=env,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE);started=True
        for _ in range(90):
            try:sql(name,'SELECT 1');break
            except subprocess.CalledProcessError:time.sleep(2)
        else:raise RuntimeError('Restore MySQL startup failed')
        sql(name,'CREATE DATABASE academic_support CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci')
        proc=subprocess.Popen(['docker','exec','-i',name,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot academic_support'],stdin=subprocess.PIPE,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
        with gzip.open(path,'rb') as stream:
            for chunk in iter(lambda:stream.read(1024*1024),b''):proc.stdin.write(chunk)
        proc.stdin.close(); error=proc.stderr.read();proc.wait()
        if proc.returncode:raise RuntimeError('SQL restore failed')
        tables=sql(name,"SELECT table_name FROM information_schema.tables WHERE table_schema='academic_support' ORDER BY table_name").splitlines()
        if not tables or 'sys_user' not in tables:raise RuntimeError('Required tables missing from backup')
        counts={table:int(sql(name,'SELECT COUNT(*) FROM academic_support.`'+table+'`')) for table in tables if re.fullmatch('[a-z_]+',table)}
        print(json.dumps({'restore_verified':True,'tables':len(tables),'counts':counts}))
        return counts
    finally:
        if started:subprocess.run(['docker','rm','-f',name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)

def upload_backup(store,path,site):
    if not re.fullmatch('[a-z0-9-]+',site):raise RuntimeError('Invalid backup site')
    counts=restore_test(path)
    key='backups/mysql/'+site+'/'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex+'.sql.gz'
    store.put(key,path)
    # Restore the downloaded object, not only the original dump.
    response=store.client.get_object(Bucket=store.bucket,Key=key)
    downloaded=path.with_name(path.name+'.verify-'+uuid.uuid4().hex)
    try:
        response['Body'].get_stream_to_file(str(downloaded))
        if sha(downloaded)!=sha(path):raise RuntimeError('Backup roundtrip mismatch')
        restored=restore_test(downloaded)
        if restored!=counts:raise RuntimeError('Restored table counts mismatch')
    finally:downloaded.unlink(missing_ok=True)
    store.json(key+'.json',{'sha256':sha(path),'size':path.stat().st_size,'restoredTableCounts':counts,'restoreVerified':True})
    print(json.dumps({'backup_uploaded':key,'sha256':sha(path),'restore_verified':True}))

def prune(store,site,days,keep):
    if days<30 or keep<7 or not re.fullmatch('[a-z0-9-]+',site):raise RuntimeError('Retention must be at least 30 days and 7 backups')
    prefix='backups/mysql/'+site+'/'
    objects=sorted((x for x in store.list(prefix) if re.fullmatch(re.escape(prefix)+r'\d{8}T\d{6}Z-[a-f0-9]{32}\.sql\.gz',x['Key'])),key=lambda x:x['Key'],reverse=True)
    cutoff=dt.datetime.now(dt.timezone.utc)-dt.timedelta(days=days);deleted=0
    # Only backups with a restore verification manifest count as good recovery points.
    verified=[x for x in objects if store.exists(x['Key']+'.json')]
    if len(verified)<keep:return
    protected={x['Key'] for x in verified[:keep]}
    for obj in objects:
        if obj['Key'] in protected or dt.datetime.fromisoformat(obj['LastModified'].replace('Z','+00:00'))>=cutoff:continue
        store.client.delete_object(Bucket=store.bucket,Key=obj['Key'])
        store.client.delete_object(Bucket=store.bucket,Key=obj['Key']+'.json');deleted+=1
    print(json.dumps({'deleted_expired_backups':deleted,'retention_days':days,'minimum_verified_backups':keep}))

def main():
    p=argparse.ArgumentParser();p.add_argument('--env',default='/home/ubuntu/.config/academic-support/storage.env')
    sub=p.add_subparsers(dest='command',required=True)
    m=sub.add_parser('migrate');m.add_argument('--root',required=True);m.add_argument('--manifest',required=True)
    r=sub.add_parser('restore-test');r.add_argument('--file',required=True)
    u=sub.add_parser('upload-backup');u.add_argument('--file',required=True);u.add_argument('--site',required=True)
    d=sub.add_parser('prune');d.add_argument('--site',required=True);d.add_argument('--days',type=int,default=30);d.add_argument('--keep',type=int,default=7)
    a=p.parse_args()
    if a.command=='restore-test':restore_test(pathlib.Path(a.file));return
    store=Store(a.env)
    if a.command=='migrate':migrate(store,a.root,a.manifest)
    elif a.command=='upload-backup':upload_backup(store,pathlib.Path(a.file),a.site)
    elif a.command=='prune':prune(store,a.site,a.days,a.keep)
if __name__=='__main__':
    try:main()
    except Exception as exc:
        print('Storage operation failed ('+type(exc).__name__+'); details suppressed to protect credentials.',file=sys.stderr);sys.exit(1)
