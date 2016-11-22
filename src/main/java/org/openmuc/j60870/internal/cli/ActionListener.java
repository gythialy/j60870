package org.openmuc.j60870.internal.cli;

public interface ActionListener {

    public void actionCalled(String actionKey) throws ActionException;

    public void quit();

}
