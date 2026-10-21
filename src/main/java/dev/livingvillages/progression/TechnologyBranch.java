package dev.livingvillages.progression;

/** Development branches. A future MILITARY branch is an additional constant, not a schema change. */
public enum TechnologyBranch {
    ECONOMY("Economy"), INFRASTRUCTURE("Infrastructure"), EXPANSION("Expansion");
    private final String title;
    TechnologyBranch(String title) { this.title=title; }
    public String title() { return title; }
}
