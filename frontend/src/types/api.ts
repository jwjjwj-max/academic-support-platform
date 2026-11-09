export interface ApiResponse<T> { code: string; message: string; data: T; timestamp: string }

export type AccountType = 'STUDENT' | 'ADMIN' | 'SYSTEM_OWNER'
export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface CurrentUser {
  id: number
  loginName: string
  displayName: string
  accountType: AccountType
  accountStatus: string
  profileReviewStatus?: ReviewStatus
  profileRejectionReason?: string
}

export interface StudentProfile {
  userId: number
  studentNo: string
  realName: string
  college: string
  major: string
  grade: number
  wechat: string
  phone?: string
  reviewStatus: ReviewStatus
  rejectionReason?: string
  profileVersion: number
  submittedAt: string
  reviewedAt?: string
}

export interface Period {
  id: number
  name: string
  shortName: string
  status: string
  registrationStart: string
  registrationEnd: string
  confirmationDeadline: string
  trackingStart: string
  trackingEnd: string
  acceptanceStart: string
  acceptanceEnd: string
  photoMin: number
  photoMax: number
  subjectWeight: number
  majorWeight: number
  gradeWeight: number
  learningStyleWeight: number
  personalityWeight: number
  gpaGapWeight: number
}

export interface PeriodTransitionPreview {
  periodId: number
  currentStatus: string
  nextStatus?: string
  allowed: boolean
  requiresConfirmation: boolean
  warnings: string[]
  blockers: string[]
  impact: Record<string, number>
}

export interface AdminAccount {
  id: number
  loginName: string
  displayName: string
  status: string
  createdAt: string
  lastLoginAt?: string
}

export type ParticipantRole = 'CLIMBER' | 'NAVIGATOR'
export type ApplicationStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'LOCKED'
export type SelectionStatus = 'UNDECIDED' | 'MATCHING_POOL' | 'WAITLIST' | 'NOT_SELECTED' | 'REMOVED_FROM_POOL'

export interface ParticipationApplication {
  id: number
  revision: number
  currentProfileVersion?: number | null
  periodId: number
  periodName: string
  studentUserId: number
  studentNo: string
  realName: string
  major: string
  grade: number
  participantRole: ParticipantRole
  cumulativeGpa: number
  subjects: string[]
  statementText: string
  learningStyles: string[]
  personalities: string[]
  applicationStatus: ApplicationStatus
  selectionStatus: SelectionStatus
  rejectionReason?: string
  submittedAt: string
  reviewedAt?: string
}

export interface MatchCandidate {
  id: number
  roundId: number
  periodId: number
  roundType: 'NORMAL' | 'LOCAL_REMATCH'
  status: string
  totalScore?: number
  scoreDetail?: Record<string, { score: number; max: number; explanation: string }>
  matchedSubjects: string[]
  navigatorApplicationId: number
  navigatorUserId: number
  navigatorName: string
  navigatorMajor: string
  navigatorGrade: number
  climberApplicationId: number
  climberUserId: number
  climberName: string
  climberMajor: string
  climberGrade: number
  navigatorDecision: string
  climberDecision: string
  rejectionReason?: string
  partnerWechat?: string
  partnerPhone?: string
  supportPairId?: number
}

export interface PairMember { userId: number; name: string; role: 'NAVIGATOR' | 'CLIMBER' }
export interface PairPhoto {
  id: number; uploaderId: number; uploaderName: string; originalFilename: string; mimeType: string; fileSize: number
  description?: string; status: string; revisionNo: number; submittedAt: string; peerConfirmedAt?: string; reviewedAt?: string
  rejectionReason?: string; canPeerConfirm: boolean; canResubmit: boolean; downloadUrl: string
}
export interface PairReflection {
  id: number; authorId: number; authorName: string; content: string; wordCount: number; status: string
  submittedAt: string; reviewedAt?: string; rejectionReason?: string; canResubmit: boolean
}
export interface SupportPairDetail {
  id: number; periodId: number; periodName: string; periodStatus: string; pairNo: string; status: string
  confirmedAt: string; completedAt?: string; acceptanceNote?: string; endedAt?: string; endReason?: string
  photoMin: number; photoMax: number; navigator: PairMember; climber: PairMember; photos: PairPhoto[]
  reflections: PairReflection[]; canSubmit: boolean; canUploadPhoto: boolean; canSubmitReflection: boolean; canAccept: boolean
}
export interface NotificationItem { id: number; periodId?: number; title: string; content: string; audienceLabel: string; createdAt: string; readAt?: string; createdByName: string }
export interface TodoItem { type: string; title: string; description: string; targetId?: number; route: string; count: number }
export interface AuditLog {
  id: number
  actorLogin: string
  action: string
  targetType: string
  targetId?: string
  reason?: string
  createdAt: string
  beforeSnapshot?: string
  afterSnapshot?: string
}
export interface GradeBatch { id:number;periodId:number;periodName:string;stage:'BEFORE'|'AFTER';label:string;originalFilename:string;status:string;totalRows:number;participantRows:number;errorRows:number;fieldMapping:Record<string,string>;createdAt:string;completedAt?:string }
export interface GradePreview { batch:GradeBatch;headers:string[];suggestedMapping:Record<string,string>;samples:Record<string,string>[];validationMessages:string[];expectedParticipants:number }
export interface AdminGrade { id:number;periodId:number;periodName:string;studentUserId:number;studentNo:string;name:string;role:ParticipantRole;stage:'BEFORE'|'AFTER';termGpa:number;termGpaRank?:number;professionalStudentCount?:number;professionalTopPercent?:number;college?:string;grade?:string;major?:string;className?:string;importBatchId:number;updatedAt:string }
export interface GradeCohortStat { id:number;periodId:number;periodName:string;stage:'BEFORE'|'AFTER';grade:string;major:string;studentCount:number;importBatchId:number;importedAt:string }
export interface StudentGradeResult { periodId:number;periodName:string;role:ParticipantRole;beforeTermGpa?:number;afterTermGpa?:number;change?:number }
export interface MoralFile { id:number;periodId:number;periodName:string;title:string;originalFilename:string;mimeType:string;fileSize:number;status:'DRAFT'|'PUBLISHED'|'WITHDRAWN';createdAt:string;publishedAt?:string;withdrawnAt?:string;downloadUrl:string }
