package com.cmclinnovations.agent.template.query;

import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.model.util.DataManifest;

public abstract class AbstractQueryTemplateFactory {
    public abstract DataManifest<String> write(QueryTemplateFactoryParameters params);

    protected abstract void reset();
}
