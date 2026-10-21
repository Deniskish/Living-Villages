package dev.livingvillages.progression;

/**
 * One evaluated requirement. Values stay structured; {@link #message()} is only the
 * human rendering of the same data, e.g. POPULATION required=10 actual=8 "Population 8 / 10".
 */
public record Requirement(RequirementType type,String subject,long required,long actual,boolean fulfilled,String message) {
    public Requirement {
        if(type==null||message==null||message.isBlank())throw new IllegalArgumentException("Requirement needs type and explanation");
        subject=subject==null?"":subject;
    }
    public String key() { return type+(subject.isEmpty()?"":":"+subject); }
    /** Text marker, so state is never conveyed by colour alone. */
    public String line() { return (fulfilled?"[✓] ":"[✗] ")+message; }
}
