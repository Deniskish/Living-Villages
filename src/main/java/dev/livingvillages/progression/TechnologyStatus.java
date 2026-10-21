package dev.livingvillages.progression;

import java.util.List;

public record TechnologyStatus(Technology technology,Stage stage,List<Requirement> requirements,String explanation) {
    public enum Stage { UNLOCKED, RESEARCHING, AVAILABLE, LOCKED }
    public TechnologyStatus { requirements=List.copyOf(requirements); }
    public String line() {
        return switch(stage) {
            case UNLOCKED -> "[✓] "+technology.name();
            case RESEARCHING -> "[~] "+technology.name()+" — researching";
            case AVAILABLE -> "[ ] "+technology.name()+" — ready to research";
            case LOCKED -> "[✗] "+technology.name()+" — "+explanation;
        };
    }
}
