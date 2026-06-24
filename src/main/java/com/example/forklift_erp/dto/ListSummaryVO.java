package com.example.forklift_erp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ListSummaryVO {
    private String type;
    private String keyword;
    private List<Card> cards = new ArrayList<>();

    public ListSummaryVO addCard(String label, Object value, String foot) {
        return addCard(label, value, foot, null);
    }

    public ListSummaryVO addMoneyCard(String label, Object value, String foot) {
        return addCard(label, value, foot, "money");
    }

    public ListSummaryVO addCard(String label, Object value, String foot, String format) {
        Card card = new Card();
        card.setLabel(label);
        card.setValue(value);
        card.setFoot(foot);
        card.setFormat(format);
        cards.add(card);
        return this;
    }

    @Data
    public static class Card {
        private String label;
        private Object value;
        private String foot;
        private String format;
    }
}
