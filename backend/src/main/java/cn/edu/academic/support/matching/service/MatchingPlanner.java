package cn.edu.academic.support.matching.service;

import cn.edu.academic.support.application.model.ParticipationApplication;
import cn.edu.academic.support.common.exception.BusinessException;
import cn.edu.academic.support.matching.dto.MatchingEvidence;
import cn.edu.academic.support.matching.dto.MatchingEvidence.Topic;
import cn.edu.academic.support.matching.dto.MatchingPolicy;
import cn.edu.academic.support.matching.dto.MatchingPolicy.Kind;
import cn.edu.academic.support.matching.dto.MatchingPolicy.Rule;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MatchingPlanner {
    private static final Map<String, String> ALIASES = Map.of("高数", "高等数学", "线代", "线性代数",
            "大英", "大学英语", "c语言程序设计", "c语言", "c程序设计", "c语言");

    record Participant(ParticipationApplication application, int grade, List<String> subjects, MatchingEvidence evidence) {
        Long id() { return application.getId(); }
        boolean navigator() { return "NAVIGATOR".equals(application.getParticipantRole()); }
    }

    record Key(long first, long second) {
        static Key of(long a, long b) { return new Key(Math.min(a, b), Math.max(a, b)); }
    }

    record Coverage(String course, String knowledgePoint, String offerQuote, String needQuote) {}

    record Basis(List<Coverage> firstToSecond, List<Coverage> secondToFirst,
                 List<String> firstUncovered, List<String> secondUncovered, List<String> sharedStudySubjects,
                 List<String> exceptions) {}

    record Candidate(Participant first, Participant second, Rule rule, int order, Basis basis, int quality) {
        List<String> subjects() {
            return java.util.stream.Stream.concat(basis.firstToSecond.stream(), basis.secondToFirst.stream())
                    .map(Coverage::course).distinct().toList();
        }
    }

    record Plan(List<Candidate> candidates, Set<Candidate> selected) {}

    private record Direction(List<Coverage> covered, List<String> uncovered) {
        int ratio() { return 1000 * covered.size() / Math.max(1, covered.size() + uncovered.size()); }
    }

    static Plan plan(MatchingPolicy policy, List<Participant> people, Set<Key> exclusions, Set<Long> affected) {
        if (people.size() > 250) throw new BusinessException("MATCHING_POOL_TOO_LARGE", "规则试算支持最多 250 名可匹配学生，请先核对入池名单");
        List<Candidate> candidates = new ArrayList<>();
        int freshman = policy.freshmanGrade();
        for (int order = 0; order < policy.rules().size(); order++) {
            Rule rule = policy.rules().get(order);
            if (!rule.enabled()) continue;
            for (Participant a : people) {
                for (Participant b : people) {
                    if (a == b || (!affected.isEmpty() && !affected.contains(a.id()) && !affected.contains(b.id()))
                            || exclusions.contains(Key.of(a.id(), b.id()))) continue;
                    Candidate candidate = evaluate(rule, order, freshman, a, b, false);
                    if (candidate != null) candidates.add(candidate);
                }
            }
        }
        return new Plan(List.copyOf(candidates), select(candidates, people.size(), policy.rules().size()));
    }

    static Candidate manualCandidate(MatchingPolicy policy, Participant first, Participant second) {
        int freshman = policy.freshmanGrade();
        for (int order = 0; order < policy.rules().size(); order++) {
            Rule rule = policy.rules().get(order);
            if (!rule.enabled()) continue;
            Candidate candidate = evaluate(rule, order, freshman, first, second, true);
            if (candidate == null) candidate = evaluate(rule, order, freshman, second, first, true);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static Candidate evaluate(Rule rule, int order, int freshman, Participant a, Participant b, boolean manual) {
        boolean sophomoreMentorsJunior = manual && rule.kind() == Kind.REGULAR && a.navigator() && !b.navigator()
                && a.grade == freshman - 1 && b.grade == freshman - 2;
        if (!sophomoreMentorsJunior && !eligible(rule, freshman, a, b)) return null;
        boolean gpaMet = !rule.requireHigherGpa()
                || a.application.getCumulativeGpa().compareTo(b.application.getCumulativeGpa()) > 0;
        if (!manual && !gpaMet) return null;
        Direction forward = coverage(a, b);
        boolean peer = rule.kind() == Kind.PEER;
        Direction reverse = peer ? coverage(b, a) : new Direction(List.of(), List.of());
        boolean coverageMet = forward.covered.size() >= rule.minimumCoverage()
                && (!peer || reverse.covered.size() >= rule.minimumCoverage());
        if (!manual && !coverageMet) return null;

        List<String> exceptions = new ArrayList<>();
        if (sophomoreMentorsJunior) exceptions.add("大二领航者带大三攀登者");
        if (!gpaMet) exceptions.add("领航者绩点未高于攀登者");
        if (!coverageMet) exceptions.add("课程覆盖未达到本期规则要求");
        List<String> shared = peer && rule.minimumCoverage() == 0 ? sharedStudySubjects(a, b) : List.of();
        int quality = peer ? 2 * Math.min(forward.ratio(), reverse.ratio()) + forward.ratio() + reverse.ratio()
                : forward.ratio() * 4;
        if (peer && rule.minimumCoverage() == 0) {
            int supportedDirections = (forward.covered.isEmpty() ? 0 : 1) + (reverse.covered.isEmpty() ? 0 : 1);
            quality = 1000 * supportedDirections + quality / 4 + Math.min(shared.size(), 8);
        }
        return new Candidate(a, b, rule, order,
                new Basis(forward.covered, reverse.covered, reverse.uncovered, forward.uncovered, shared, List.copyOf(exceptions)), quality);
    }

    private static boolean eligible(Rule rule, int freshman, Participant a, Participant b) {
        if (rule.kind() == Kind.PEER) {
            return a.grade == freshman && b.grade == freshman && a.id() < b.id();
        }
        if (!a.navigator() || b.navigator()) return false;
        if (rule.kind() == Kind.FRESHMAN_SUPPORT) {
            return b.grade == freshman && (a.grade == freshman - 1 || rule.allowOlderNavigator() && a.grade == freshman - 2);
        }
        return b.grade < freshman && (a.grade == b.grade || rule.allowOlderNavigator() && a.grade < b.grade);
    }

    private static List<String> sharedStudySubjects(Participant first, Participant second) {
        Map<String, Topic> secondNeeds = topics(second, false);
        return topics(first, false).entrySet().stream().filter(entry -> secondNeeds.containsKey(entry.getKey()))
                .map(entry -> entry.getValue().course()).toList();
    }

    private static Direction coverage(Participant helper, Participant learner) {
        Map<String, Topic> supply = topics(helper, true);
        List<Coverage> matched = new ArrayList<>();
        List<String> uncovered = new ArrayList<>();
        for (var entry : topics(learner, false).entrySet()) {
            Topic need = entry.getValue();
            Topic offer = supply.get(entry.getKey());
            String point = need.knowledgePoint();
            if (offer != null && (point == null || point.isBlank() || normalized(point).equals(normalized(offer.knowledgePoint())))) {
                matched.add(new Coverage(need.course(), point, offer.quote(), need.quote()));
            } else {
                uncovered.add(point == null || point.isBlank() ? need.course() : need.course() + " · " + point);
            }
        }
        return new Direction(List.copyOf(matched), List.copyOf(uncovered));
    }

    private static Map<String, Topic> topics(Participant person, boolean supply) {
        Map<String, Topic> facts = new LinkedHashMap<>();
        if (person.navigator() == supply) {
            String label = supply ? "报名科目（可提供）" : "报名科目（需要帮助）";
            person.subjects.forEach(course -> facts.put(canonical(course), new Topic(course, null, label + " · " + course)));
        }
        (supply ? person.evidence.offers() : person.evidence.needs()).forEach(topic -> facts.put(canonical(topic.course()), topic));
        return facts;
    }

    private static String canonical(String value) {
        String key = normalized(value);
        return ALIASES.getOrDefault(key, key);
    }

    static boolean mentionsCourse(String quote, String course) {
        String text = normalized(quote);
        String canonical = canonical(course);
        return !canonical.isBlank() && (text.contains(normalized(course)) || text.contains(canonical)
                || ALIASES.entrySet().stream().anyMatch(alias -> alias.getValue().equals(canonical) && text.contains(alias.getKey())));
    }

    static boolean mentionsPoint(String quote, String point) {
        return point == null || point.isBlank() || normalized(quote).contains(normalized(point));
    }

    private static String normalized(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\s·•]", "");
    }

    private static Set<Candidate> select(List<Candidate> candidates, int participantCount, int ruleCount) {
        if (candidates.isEmpty()) return Set.of();
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        try {
            model.options.time_abort = 7000;
            Map<Long, Expression> occupancy = new HashMap<>();
            Map<Kind, Expression> quotas = new HashMap<>();
            long maxPairs = participantCount / 2L;
            long[] priority = new long[ruleCount];
            priority[ruleCount - 1] = 4000 * maxPairs + 1;
            // One extra pair in an earlier rule outweighs all later rules and all course coverage.
            for (int i = ruleCount - 2; i >= 0; i--) priority[i] = priority[i + 1] * (maxPairs + 1);
            for (int i = 0; i < candidates.size(); i++) {
                Candidate pair = candidates.get(i);
                var variable = model.newVariable("pair" + i).binary().weight(priority[pair.order] + pair.quality);
                for (Long id : List.of(pair.first.id(), pair.second.id())) {
                    occupancy.computeIfAbsent(id, key -> model.newExpression("person" + key).upper(1)).set(variable, 1);
                }
                if (pair.rule.maximumPairs() != null) {
                    quotas.computeIfAbsent(pair.rule.kind(), key -> model.newExpression("limit" + key)
                            .upper(pair.rule.maximumPairs())).set(variable, 1);
                }
            }
            Optimisation.Result result = model.maximise();
            if (!result.getState().isOptimal()) {
                throw new BusinessException("MATCHING_SOLVER_LIMIT", "未能在时限内确认最优方案，请检查规则和候选范围后重新试算");
            }
            var selected = new java.util.HashSet<Candidate>();
            for (int i = 0; i < candidates.size(); i++) {
                if (result.doubleValue(i) > 0.5) selected.add(candidates.get(i));
            }
            return Set.copyOf(selected);
        } finally {
            model.dispose();
        }
    }

    private MatchingPlanner() {}
}
