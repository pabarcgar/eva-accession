/*
 *
 * Copyright 2018 EMBL - European Bioinformatics Institute
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.ebi.eva.accession.core.persistence;


import uk.ac.ebi.ampt2d.commons.accession.generators.monotonic.MonotonicRange;
import uk.ac.ebi.ampt2d.commons.accession.persistence.BasicSpringDataRepositoryDatabaseService;
import uk.ac.ebi.ampt2d.commons.accession.persistence.IAccessionedObjectCustomRepository;
import uk.ac.ebi.ampt2d.commons.accession.persistence.jpa.monotonic.service.MonotonicDatabaseService;
import uk.ac.ebi.eva.accession.core.ISubmittedVariant;

import java.util.Collection;

public class SubmittedVariantAccessioningDatabaseService
        extends BasicSpringDataRepositoryDatabaseService<ISubmittedVariant, SubmittedVariantEntity, Long>
        implements MonotonicDatabaseService<ISubmittedVariant, String> {

    public SubmittedVariantAccessioningDatabaseService(SubmittedVariantAccessioningRepository repository, IAccessionedObjectCustomRepository customRepository) {
        super(repository,
              customRepository,
              accessionWrapper -> new SubmittedVariantEntity(accessionWrapper.getAccession(),
                                                             accessionWrapper.getHash(),
                                                             accessionWrapper.getData()),
              submittedVariant -> submittedVariant);
    }

    @Override
    public long[] getAccessionsInRanges(Collection<MonotonicRange> ranges) {
        return new long[0];
    }
}
