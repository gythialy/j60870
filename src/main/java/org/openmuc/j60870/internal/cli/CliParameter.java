package org.openmuc.j60870.internal.cli;

public abstract class CliParameter {

    String name;
    String description;
    boolean optional;
    boolean selected;

    CliParameter(CliParameterBuilder builder) {
        name = builder.name;
        description = builder.description;
        optional = builder.optional;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the optional
     */
    public boolean isOptional() {
        return optional;
    }

    public boolean isSelected() {
        return selected;
    }

    abstract int parse(String[] args, int i) throws CliParseException;

    abstract int appendSynopsis(StringBuilder sb);

    abstract void appendDescription(StringBuilder sb);

}
