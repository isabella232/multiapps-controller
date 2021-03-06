package org.cloudfoundry.multiapps.controller.core.cf.metadata.criteria;

public class FinalizingBuilder {
    private MtaMetadataCriteriaBuilder mtaMetadataCriteriaBuilder;

    public FinalizingBuilder(MtaMetadataCriteriaBuilder mtaMetadataCriteriaBuilder) {
        this.mtaMetadataCriteriaBuilder = mtaMetadataCriteriaBuilder;
    }

    public MtaMetadataCriteriaBuilder and() {
        return mtaMetadataCriteriaBuilder;
    }

    public MtaMetadataCriteria build() {
        String query = String.join(",", mtaMetadataCriteriaBuilder.getQueries());
        return new MtaMetadataCriteria(query);
    }
}
