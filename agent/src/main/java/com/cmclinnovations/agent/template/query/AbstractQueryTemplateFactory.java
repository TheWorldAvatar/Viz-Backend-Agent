package com.cmclinnovations.agent.template.query;

import java.util.Queue;

import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;

public abstract class AbstractQueryTemplateFactory {
    public abstract Queue<String> write(QueryTemplateFactoryParameters params);

    protected abstract void reset();
}
