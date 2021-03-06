/*
 * Copyright 2012 Harald Wellmann
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.ops4j.pax.exam.spi.war;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.glassfish.embeddable.archive.ScatteredArchive;
import org.glassfish.embeddable.archive.ScatteredArchive.Type;
import org.ops4j.io.StreamUtils;
import org.ops4j.io.ZipExploder;
import org.ops4j.pax.exam.TestContainerException;
import org.ops4j.pax.exam.options.WarProbeOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

/**
 * Builds a WAR according to a {@link WarProbeOption}.
 * 
 * @author Harald Wellmann
 * 
 */
public class WarBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(WarBuilder.class);

    /**
     * Temporary directory for assembling the WAR probe.
     */
    private File tempDir;

    /**
     * Option used to configure the WAR.
     */
    private WarProbeOption option;

    /**
     * Composite regular expression for filtering classpath components.
     */
    private Pattern filterPattern;

    /**
     * Constructs a WAR builder for the given option.
     * 
     * @param option
     *            WAR probe option
     */
    public WarBuilder(WarProbeOption option) {
        this.option = option;
        this.tempDir = Files.createTempDir();
    }

    /**
     * Builds a WAR from the given option.
     * 
     * @return file URI referencing the WAR in a temporary directory
     */
    public URI buildWar() {
        this.tempDir = Files.createTempDir();
        if (option.getName() == null) {
            option.name(UUID.randomUUID().toString());
        }
        processClassPath();
        try {
            File webResourceDir = getWebResourceDir();
            ScatteredArchive sar = new ScatteredArchive(option.getName(), Type.WAR, webResourceDir);
            for (String library : option.getLibraries()) {

                File file = toLocalFile(library);
                LOG.debug("including library {}", file);
                sar.addClassPath(file);
            }
            URI warUri = sar.toURI();
            LOG.info("WAR probe = {}", warUri);
            return warUri;
        }
        catch (IOException exc) {
            throw new TestContainerException(exc);
        }
    }

    private File toLocalFile(String anyUri) throws IOException {
        URI uri = null;
        try {
            uri = new URI(anyUri);
            File file = new File(uri);
            return file;
        }
        catch (URISyntaxException exc) {
            throw new TestContainerException(exc);
        }
        catch (IllegalArgumentException exc) {
            InputStream is = uri.toURL().openStream();
            File tempFile = File.createTempFile("paxexam", ".jar");
            OutputStream os = new FileOutputStream(tempFile);
            StreamUtils.copyStream(is, os, true);
            return tempFile;
        }
    }

    private void processClassPath() {
        if (option.isClassPathEnabled()) {
            buildClassPathPattern();
            String classpath = System.getProperty("java.class.path");
            String[] pathElems = classpath.split(File.pathSeparator);

            for (String pathElem : pathElems) {
                File file = new File(pathElem);
                if (file.exists()) {
                    String path = file.getAbsolutePath();
                    Matcher matcher = filterPattern.matcher(path);
                    if (!matcher.find()) {
                        option.library(path);
                    }
                }
            }
        }
    }

    private File getWebResourceDir() throws IOException {
        File webResourceDir = new File(tempDir, "webapp");
        LOG.debug("building webapp in {}", webResourceDir);
        ZipExploder exploder = new ZipExploder();
        webResourceDir.mkdir();

        if (option.getOverlays().isEmpty()) {
            option.overlay("src/main/webapp");
        }
        for (String overlay : option.getOverlays()) {
            File file = toFile(overlay);
            if (file.exists()) {
                if (file.isDirectory()) {
                    copyDirectory(file, webResourceDir);
                }
                else {
                    exploder.processFile(file.getAbsolutePath(), webResourceDir.getAbsolutePath());
                }
            }
        }

        File metaInfDir = new File(webResourceDir, "META-INF");
        metaInfDir.mkdir();
        for (String metaInfResource : option.getMetaInfResources()) {
            File source = new File(metaInfResource);
            if (source.isDirectory()) {
                copyDirectory(source, metaInfDir);
            }
            else {
                Files.copy(source, new File(metaInfDir, source.getName()));
            }
        }

        File webInfDir = new File(webResourceDir, "WEB-INF");
        webInfDir.mkdir();
        for (String webInfResource : option.getWebInfResources()) {
            File source = new File(webInfResource);
            if (source.isDirectory()) {
                copyDirectory(source, webInfDir);
            }
            else {
                Files.copy(source, new File(webInfDir, source.getName()));
            }
        }

        File resourceDir = new File(webResourceDir, "WEB-INF/classes");
        resourceDir.mkdir();
        for (Class<?> klass : option.getClasses()) {
            addClass(klass, resourceDir);
        }

        for (String resource : option.getResources()) {
            addResource(resource, resourceDir);
        }

        File beansXml = new File(webInfDir, "beans.xml");
        if (!beansXml.exists()) {
            beansXml.createNewFile();
        }
        return webResourceDir;
    }

    private void addClass(Class<?> klass, File resourceDir) throws IOException {
        String resource = "/" + klass.getName().replaceAll("\\.", "/") + ".class";
        addResource(resource, resourceDir);
        for (Class<?> innerClass : klass.getClasses()) {
            addClass(innerClass, resourceDir);
        }
    }

    private void addResource(String resource, File resourceDir) throws IOException {
        InputStream is = getClass().getResourceAsStream("/" + resource);
        File target = new File(resourceDir, resource);
        target.getParentFile().mkdirs();
        FileOutputStream os = new FileOutputStream(target);
        StreamUtils.copyStream(is, os, true);
    }

    private File toFile(String uriString) {
        try {
            URI uri = new URI(uriString);
            try {
                return new File(uri);
            }
            catch (IllegalArgumentException exc) {
                InputStream is = uri.toURL().openStream();
                File tempFile = File.createTempFile("pax-exam", ".tmp");
                OutputStream os = new FileOutputStream(tempFile);
                StreamUtils.copyStream(is, os, true);
                return tempFile;
            }
        }
        catch (URISyntaxException exc) {
            throw new TestContainerException(exc);
        }
        catch (IOException exc) {
            throw new TestContainerException(exc);
        }
    }

    private void copyDirectory(File fromDir, File toDir) throws IOException {
        for (File file : fromDir.listFiles()) {
            if (file.isDirectory()) {
                File targetDir = new File(toDir, file.getName());
                targetDir.mkdir();
                copyDirectory(file, targetDir);
            }
            else {
                Files.copy(file, new File(toDir, file.getName()));
            }
        }
    }

    private void buildClassPathPattern() {
        List<String> classpathFilters = option.getClassPathFilters();
        if (classpathFilters.isEmpty()) {
            filterPattern = Pattern.compile("^$");
            return;
        }

        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < classpathFilters.size(); i++) {
            if (i > 0) {
                buffer.append("|");
            }
            buffer.append("(");
            buffer.append(classpathFilters.get(i));
            buffer.append(")");
        }
        String disjunction = buffer.toString();
        filterPattern = Pattern.compile(disjunction);
    }
}
