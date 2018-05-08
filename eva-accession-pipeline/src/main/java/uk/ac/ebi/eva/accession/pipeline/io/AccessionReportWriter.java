/*
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
 */
package uk.ac.ebi.eva.accession.pipeline.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;

import uk.ac.ebi.ampt2d.commons.accession.core.AccessionWrapper;
import uk.ac.ebi.eva.accession.core.ISubmittedVariant;
import uk.ac.ebi.eva.accession.core.SubmittedVariant;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

// TODO make AccessionReportWriter implement ItemStreamWriter<AccessionWrapper<ISubmittedVariant, String, Long>>
// when accession-commons 0.3 is used. Not worth to do it right now because
// SubmittedVariantAccessioningService returns a Map.
public class AccessionReportWriter implements ItemStream {

    private static final String VCF_MISSING_VALUE = ".";

    private static final String IS_HEADER_WRITTEN_KEY = "AccessionReportWriter_isHeaderWritten";

    private static final String IS_HEADER_WRITTEN_VALUE = "true";   // use string because ExecutionContext doesn't support boolean

    private static final Logger logger = LoggerFactory.getLogger(AccessionReportWriter.class);

    private final File output;

    private FastaSequenceReader fastaSequenceReader;

    private BufferedWriter fileWriter;

    private String accessionPrefix;

    public AccessionReportWriter(File output, FastaSequenceReader fastaSequenceReader) throws IOException {
        this.fastaSequenceReader = fastaSequenceReader;
        this.output = output;
        this.accessionPrefix = "ss";
    }

    public String getAccessionPrefix() {
        return accessionPrefix;
    }

    public void setAccessionPrefix(String accessionPrefix) {
        this.accessionPrefix = accessionPrefix;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        boolean isHeaderAlreadyWritten = IS_HEADER_WRITTEN_VALUE.equals(executionContext.get(IS_HEADER_WRITTEN_KEY));
        if (output.exists() && !isHeaderAlreadyWritten) {
            logger.warn("According to the job's execution context, the accession report should not exist, but it does" +
                                " exist. The AccessionReportWriter will append to the file, but it's possible that " +
                                "there will be 2 non-contiguous header sections in the report VCF. This can happen if" +
                                " the job execution context was not properly retrieved from the job repository.");
        }
        try {
            boolean append = true;
            this.fileWriter = new BufferedWriter(new FileWriter(this.output, append));
            if (!isHeaderAlreadyWritten) {
                writeHeader();
                executionContext.put(IS_HEADER_WRITTEN_KEY, IS_HEADER_WRITTEN_VALUE);
            }
        } catch (IOException e) {
            throw new ItemStreamException(e);
        }
    }

    private void writeHeader() throws IOException {
        fileWriter.write("##fileformat=VCFv4.2");
        fileWriter.newLine();
        fileWriter.write("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO");
        fileWriter.newLine();
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {

    }

    @Override
    public void close() throws ItemStreamException {
        try {
            fileWriter.close();
        } catch (IOException e) {
            throw new ItemStreamException(e);
        }
    }

//    public void write(Map<Long, ISubmittedVariant> accessions) throws IOException {
    public void write(List<AccessionWrapper<ISubmittedVariant, String, Long>> accessions) throws IOException {
        if (fileWriter == null) {
            throw new IOException("The file " + output + " was not opened properly. Hint: Check that the code " +
                                          "called AccessionReportWriter::open");
        }
//        for (Map.Entry<Long, ISubmittedVariant> variant : accessions.entrySet()) {
//            writeVariant(variant.getKey(), variant.getValue());
//        }
        for (AccessionWrapper<ISubmittedVariant, String, Long> variant : accessions) {
            writeVariant(variant.getAccession(), variant.getData());
        }
        fileWriter.flush();
    }

    private void writeVariant(Long id, ISubmittedVariant normalizedVariant) throws IOException {
        ISubmittedVariant variant = denormalizeVariant(normalizedVariant);
        String vcfLine = variantToVcfLine(id, variant);
        fileWriter.write(vcfLine);
        fileWriter.newLine();
    }

    private ISubmittedVariant denormalizeVariant(ISubmittedVariant normalizedVariant) {
        if (normalizedVariant.getReferenceAllele().isEmpty() || normalizedVariant.getAlternateAllele().isEmpty()) {
            if (fastaSequenceReader.doesContigExist(normalizedVariant.getContig())) {
                return createVariantWithContextBase(normalizedVariant);
            } else {
                throw new IllegalArgumentException("Contig '" + normalizedVariant.getContig()
                                                           + "' does not appear in the fasta file ");
            }
        } else {
            return normalizedVariant;
        }
    }

    private ISubmittedVariant createVariantWithContextBase(ISubmittedVariant normalizedVariant) {
        long newStart;
        String newReference;
        String newAlternate;
        String contextBase;
        if (normalizedVariant.getStart() == 1) {
            // VCF 4.2 section 1.4.1.4. REF: "the REF and ALT Strings must include the base before the event unless the
            // event occurs at position 1 on the contig in which case it must include the base after the event"
            newStart = normalizedVariant.getStart() + 1;
            contextBase = fastaSequenceReader.getSequence(normalizedVariant.getContig(), newStart, newStart);
            newReference = normalizedVariant.getReferenceAllele() + contextBase;
            newAlternate = normalizedVariant.getAlternateAllele() + contextBase;
        } else {
            newStart = normalizedVariant.getStart() - 1;
            contextBase = fastaSequenceReader.getSequence(normalizedVariant.getContig(), newStart, newStart);
            newReference = contextBase + normalizedVariant.getReferenceAllele();
            newAlternate = contextBase + normalizedVariant.getAlternateAllele();
        }

        if (contextBase.isEmpty()) {
            throw new IllegalStateException("fastaSequenceReader should have returned a non-empty sequence");
        } else {
            return new SubmittedVariant(normalizedVariant.getAssemblyAccession(),
                                        normalizedVariant.getTaxonomyAccession(),
                                        normalizedVariant.getProjectAccession(),
                                        normalizedVariant.getContig(),
                                        newStart,
                                        newReference,
                                        newAlternate,
                                        normalizedVariant.isSupportedByEvidence());
        }
    }

    protected String variantToVcfLine(Long id, ISubmittedVariant variant) {
        String variantLine = String.join("\t",
                                         variant.getContig(),
                                         Long.toString(variant.getStart()),
                                         accessionPrefix + id,
                                         variant.getReferenceAllele(),
                                         variant.getAlternateAllele(),
                                         VCF_MISSING_VALUE, VCF_MISSING_VALUE, VCF_MISSING_VALUE);
        return variantLine;
    }

}
