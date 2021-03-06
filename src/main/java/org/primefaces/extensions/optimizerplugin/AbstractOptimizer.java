/*
 * Copyright 2011 PrimeFaces Extensions.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * $Id$
 */

package org.primefaces.extensions.optimizerplugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

import com.google.common.io.Files;

/**
 * Basis abstract class for Google Closure Compiler / YUI Compressor Optimizers.
 *
 * @author  Oleg Varaksin / last modified by $Author$
 * @version $Revision$
 * @since   0.1
 */
public abstract class AbstractOptimizer {

	private static final String AGGREGATED_FILE_EXTENSION = ".aggr";

	private long sizeTotalOriginal = 0;

	private long sizeTotalOptimized = 0;

	public abstract void optimize(final ResourcesSetAdapter rsa, final Log log) throws MojoExecutionException;

	protected File getFileWithSuffix(final String path, final String suffix) throws IOException {
		// get file extension
		String extension = FileUtils.extension(path);
		if (StringUtils.isNotEmpty(extension)) {
			extension = "." + extension;
		}

		// path of file with suffix
		String pathSuffix = FileUtils.removeExtension(path) + suffix + extension;

		// create a new file with suffix
		File outputFile = new File(pathSuffix);
		Files.touch(outputFile);

		return outputFile;
	}

	protected File aggregateFiles(final ResourcesSetAdapter rsa, final Charset cset, final Log log, final boolean delimeters)
	    throws IOException {
		int filesCount = rsa.getFiles().size();
		if (rsa.getAggregation().getPrependedFile() != null) {
			// statistic
			addToOriginalSize(rsa.getAggregation().getPrependedFile());
			filesCount++;
		}

		if (filesCount > 1) {
			log.info("Aggregation is running ...");
		}

		File outputFile = getOutputFile(rsa);

		long sizeBefore = outputFile.length();

		if (rsa.getAggregation().getPrependedFile() != null) {
			// write / append to be prepended file into / to the output file
			prependFile(rsa.getAggregation().getPrependedFile(), outputFile, cset, rsa);
		}

		for (File file : rsa.getFiles()) {
			// statistic
			addToOriginalSize(file);

			Reader in = getReader(rsa, file);
			StringWriter writer = new StringWriter();
			IOUtil.copy(in, writer);

			if (delimeters && outputFile.length() > 0) {
				// append semicolon to the new file in order to avoid invalid JS code
				Files.append(";", outputFile, cset);
			}

			// write / append content into / to the new file
			Files.append(writer.toString(), outputFile, cset);
			IOUtil.close(in);
		}

		// statistic
		addToOptimizedSize(outputFile.length() - sizeBefore);

		if (filesCount > 1) {
			log.info(filesCount + " files were successfully aggregated.");
		}

		return outputFile;
	}

	protected void deleteFilesIfNecessary(final ResourcesSetAdapter rsa, final Log log) {
		if (rsa.getAggregation().isRemoveIncluded() && rsa.getFiles().size() > 0) {
			for (File file : rsa.getFiles()) {
				if (file.exists() && !file.delete()) {
					log.warn("File " + file.getName() + " could not be deleted after aggregation.");
				}
			}
		}
	}

	protected void renameOutputFileIfNecessary(final ResourcesSetAdapter rsa, final File outputFile) throws IOException {
		if (outputFile != null && outputFile.exists()) {
			FileUtils.rename(outputFile, rsa.getAggregation().getOutputFile());
		}
	}

	protected void prependFile(final File prependedFile, final File outputFile, final Charset cset, final ResourcesSetAdapter rsa)
	    throws IOException {
		Reader in = getReader(rsa, prependedFile);
		StringWriter writer = new StringWriter();
		IOUtil.copy(in, writer);

		writer.write(System.getProperty("line.separator"));

		// write / append compiled content into / to the new file
		Files.append(writer.toString(), outputFile, cset);
		IOUtil.close(in);
	}

	protected File getOutputFile(final ResourcesSetAdapter rsa) throws IOException {
		File outputFile = rsa.getAggregation().getOutputFile();

		// prevent overwriting of existing CSS or JS file with the same name as the output file
		File aggrFile = new File(FileUtils.removeExtension(outputFile.getCanonicalPath()) + AGGREGATED_FILE_EXTENSION);
		Files.createParentDirs(aggrFile);
		Files.touch(aggrFile);

		return aggrFile;
	}

	protected Reader getReader(final ResourcesSetAdapter rsAdapter, final File file)
	    throws FileNotFoundException, UnsupportedEncodingException {
		return new InputStreamReader(new FileInputStream(file), rsAdapter.getEncoding());
	}

	protected void addToOriginalSize(final File file) {
		sizeTotalOriginal = sizeTotalOriginal + file.length();
	}

	protected void addToOptimizedSize(final File file) {
		sizeTotalOptimized = sizeTotalOptimized + file.length();
	}

	protected void addToOptimizedSize(final long size) {
		sizeTotalOptimized = sizeTotalOptimized + size;
	}

	protected long getTotalOriginalSize() {
		return sizeTotalOriginal;
	}

	protected long getTotalOptimizedSize() {
		return sizeTotalOptimized;
	}
}
