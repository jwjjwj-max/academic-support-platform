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
                 List<String> firstUncovered, List<String> secondUncovered) {}

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
                            || !eligible(rule, freshman, a, b) || exclusions.contains(Key.of(a.id(), b.id()))) continue;
                    Direction forward = coverage(a, b);
                    if (forward.covered.size() < rule.minimumCoverage()) continue;
                    boolean peer = rule.kind() == Kind.PEER;
                    Direction reverse = peer ? coverage(b, a) : new Direction(List.of(), List.of());
                    if (peer && reverse.covered.size() < rule.minimumCoverage()) continue;
                    int quality = peer ? 2 * Math.min(forward.ratio(), reverse.ratio()) + forward.ratio() + reverse.ratio()
                            : forward.ratio() * 4;
                    candidates.add(new Candidate(a, b, rule, order,
                            new Basis(forward.covered, reverse.covered, reverse.uncovered, forward.uncovered), quality));
                }
            }
        }
        return new Plan(List.copyOf(candidates), select(candidates, people.size(), policy.rules().size()));
    }

    private static boolean eligible(Rule rule, int freshman, Participant a, Participant b) {
        if (rule.kind() == Kind.PEER) {
            return a.grade == freshman && b.grade == freshman && a.id() < b.id();
        }
        if (!a.navigator() || b.navigator()) return false;
        if (rule.kind() == Kind.FRESHMAN_SUPPORT) return a.grade == freshman - 1 && b.grade == freshman;
        boolean eligibleGrade = b.grade < freshman && (a.grade == b.grade || rule.allowOlderNavigator() && a.grade < b.grade);
        return eligibleGrade && (!rule.requireHigherGpa()
                || a.application.getCumulativeGpa().compareTo(b.application.getCumulativeGpa()) > 0);
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
