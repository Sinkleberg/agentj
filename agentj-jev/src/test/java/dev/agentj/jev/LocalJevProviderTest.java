package dev.agentj.jev;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalJevProviderTest {
    private static LinkedHashMap<String, JevQuestion> questions() {
        var questions = new LinkedHashMap<String, JevQuestion>();
        questions.put("team", JevQuestion.Choice.of(
                "Which team should handle this?",
                Map.of("billing", "payments and refunds", "technical", "bugs and outages")));
        questions.put("urgent", new JevQuestion.Noul("Does this require same-day attention?"));
        questions.put("severity", JevQuestion.Score.of(
                "How severe is the impact?", "Minor", "Moderate", "Major", "Critical"));
        return questions;
    }

    @Test
    void makesDeterministicTypedDecisionsWithoutNetwork() throws Exception {
        var provider = new LocalJevProvider();
        var first = provider.ask(Map.of("message", "Customer was charged twice and needs a refund today."), questions());
        var second = provider.ask(Map.of("message", "Customer was charged twice and needs a refund today."), questions());

        assertEquals(first.answers(), second.answers());
        assertEquals("billing", ((JevAnswer.Choice) first.answer("team")).choice());
        assertTrue(((JevAnswer.Noul) first.answer("urgent")).isTrue(0.80));
        assertEquals(2.0 / 3.0, ((JevAnswer.Score) first.answer("severity")).score());
        assertEquals(0L, first.usage().inputTokens());
        assertEquals(0L, first.usage().outputTokens());
    }

    @Test
    void negativeNoulKeepsItsProbabilityAndConfidenceDistinct() throws Exception {
        var questions = new LinkedHashMap<String, JevQuestion>();
        questions.put("urgent", new JevQuestion.Noul("Does this require same-day attention?"));

        var result = new LocalJevProvider().ask(Map.of("message", "Just a general question."), questions);
        var answer = (JevAnswer.Noul) result.answer("urgent");

        assertFalse(answer.isTrue(0.80));
        assertEquals(0.08, answer.noul());
        assertEquals(0.92, answer.confidence());
    }

    @Test
    void urgentDecisionIsLocaleIndependent() {
        var questions = new LinkedHashMap<String, JevQuestion>();
        questions.put("urgent", new JevQuestion.Noul("Does this require same-day attention?"));

        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ENGLISH);
            var english = (JevAnswer.Noul) new LocalJevProvider()
                    .ask(Map.of("message", "CRITICAL outage"), questions)
                    .answer("urgent");

            Locale.setDefault(new Locale("tr", "TR"));
            var turkish = (JevAnswer.Noul) new LocalJevProvider()
                    .ask(Map.of("message", "CRITICAL outage"), questions)
                    .answer("urgent");

            assertTrue(english.isTrue(0.80));
            assertTrue(turkish.isTrue(0.80));
            assertEquals(english.noul(), turkish.noul());
        } finally {
            Locale.setDefault(original);
        }
    }
}
