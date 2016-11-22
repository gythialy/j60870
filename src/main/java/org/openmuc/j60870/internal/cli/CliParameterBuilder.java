package org.openmuc.j60870.internal.cli;

public class CliParameterBuilder {

    final String name;
    String description;
    boolean optional = true;

    public CliParameterBuilder(String name) {
        this.name = name;
    }

    public CliParameterBuilder setDescription(String description) {
        this.description = description;
        return this;
    }

    public CliParameterBuilder setMandatory() {
        optional = false;
        return this;
    }

    public LongCliParameter buildLongParameter(String parameterName, long defaultValue) {
        return new LongCliParameter(this, parameterName, defaultValue);
    }

    public LongCliParameter buildLongParameter(String parameterName) {
        return new LongCliParameter(this, parameterName);
    }

    public IntCliParameter buildIntegerParameter(String parameterName, int defaultValue) {
        return new IntCliParameter(this, parameterName, defaultValue);
    }

    public IntCliParameter buildIntParameter(String parameterName) {
        return new IntCliParameter(this, parameterName);
    }

    public StringCliParameter buildStringParameter(String parameterName, String defaultValue) {
        return new StringCliParameter(this, parameterName, defaultValue);
    }

    public StringCliParameter buildStringParameter(String parameterName) {
        return new StringCliParameter(this, parameterName);
    }

    public FlagCliParameter buildFlagParameter() {
        return new FlagCliParameter(this);
    }

}
