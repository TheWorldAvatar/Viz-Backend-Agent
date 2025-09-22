package com.cmclinnovations.agent.template.query;

import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;

public abstract class AbstractQueryTemplateFactory {
    public abstract String write(QueryTemplateFactoryParameters params);

    protected abstract void reset();
}
