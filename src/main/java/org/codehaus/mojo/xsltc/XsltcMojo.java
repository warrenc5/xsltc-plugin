/*
 The MIT License

 Copyright (c) 2004, The Codehaus

 Permission is hereby granted, free of charge, to any person obtaining a copy of
 this software and associated documentation files (the "Software"), to deal in
 the Software without restriction, including without limitation the rights to
 use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 of the Software, and to permit persons to whom the Software is furnished to do
 so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THEfilter
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */
 /*
 * Created on Aug 17, 2006
 */
package org.codehaus.mojo.xsltc;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamSource;
import org.apache.maven.artifact.DependencyResolutionRequiredException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

/**
 * Compiles XSLT stylesheets into Xalan translets.
 *
 * @goal compile
 *
 * @phase process-sources
 *
 * @requiresDependencyResolution runtime
 *
 * @author Matt Whitlock
 */
public class XsltcMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter expression="${project}" @required
     */
    protected MavenProject project;
    /**
     * XSLT stylesheets to be compiled.
     *
     * @parameter @required
     */
    private File[] stylesheets;
    /**
     * Package name for compiled translets.
     *
     * @parameter
     */
    private String packageName;
    /**
     * Directory in which to place compiled translets.
     *
     * @parameter expression="${project.build.outputDirectory}" @required
     */
    private File outputDirectory;
    /**
     * Set this to org.apache.xalan.xsltc.trax.TransformerFactoryImpl to use the
     * apache.org xalan
     *
     * @parameter expression="${project.build.outputDirectory}" @required
     */
    private String transformerFactoryClass;
    private Log log;

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        log = getLog();

        List<URL> urls = new ArrayList<URL>();
        int i = 0;

        try {
            for (Object o : project.getCompileClasspathElements()) {
                try {
                    urls.add(new URL("file:///" + o.toString()));
                } catch (MalformedURLException ex) {
                    log.info(ex.getMessage(), ex);
                } catch (IOException ex) {
                    log.info(ex.getMessage(), ex);
                }
            }
        } catch (DependencyResolutionRequiredException ex) {
            log.info(ex.getMessage(), ex);
        }

        try {
            for (Object o : project.getRuntimeClasspathElements()) {
                try {
                    urls.add(new URL("file:///" + o.toString()));
                } catch (MalformedURLException ex) {
                    log.info(ex.getMessage(), ex);
                } catch (IOException ex) {
                    log.info(ex.getMessage(), ex);
                }
            }
        } catch (DependencyResolutionRequiredException ex) {
            log.info(ex.getMessage(), ex);
        }

        log.debug("resources added to classloader " + Arrays.asList(urls).toString());
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        log.debug("context classloader: " + Arrays.asList(((URLClassLoader)cl).getURLs()).toString());
        try {
            urls.add(new File(project.getBuild().getOutputDirectory()).toURI().toURL());
        } catch (MalformedURLException ex) {
            Logger.getLogger(XsltcMojo.class.getName()).log(Level.SEVERE, null, ex);
        }
        //Thread.currentThread().setContextClassLoader(
        final URLClassLoader rl = new URLClassLoader(urls.toArray(new URL[urls.size()]),cl);
                //);
        log.debug("classloader : " + Arrays.asList(rl.getURLs()).toString());


        TransformerFactory tf = null;
//org.apache.xalan.xsltc.trax.TransformerFactoryImpl
        if (transformerFactoryClass != null) {
            tf = TransformerFactory.newInstance(transformerFactoryClass, rl);
        } else {
            tf = TransformerFactory.newInstance();
        }

        URIResolver ui;
        tf.setURIResolver(ui = new URIResolver() {

            public Source resolve(String href, String base) throws TransformerException {
                log.info("resolving " + href + " " + base);
                StreamSource inputSource = null;
                if (href.startsWith("resource:")) {
                    inputSource = new StreamSource(rl.getResourceAsStream(href.substring(9)));
                } else {
                    try {
                        inputSource = new StreamSource(new URL(href).openStream());
                    } catch (MalformedURLException ex) {
                        log.error(ex.getMessage());
                    } catch (IOException ex) {
                        log.error(ex.getMessage());
                    }
                }

                return inputSource;
            }
        });
        
        tf.setErrorListener(new ErrorListener() {
            public void warning(TransformerException exception) throws TransformerException {
                logError(exception);
            }

            public void error(TransformerException exception) throws TransformerException {
                logError(exception);
            }

            public void fatalError(TransformerException exception) throws TransformerException {
                logError(exception);
            }

            private void logError(TransformerException exception) throws TransformerException {
                log.error(exception.getLocator() + " " + exception.getMessageAndLocation(), exception);
            }
        });

        boolean fail = false;
        for (File stylesheet : stylesheets) {
            try {
                tf.setAttribute("package-name", packageName);
                tf.setAttribute("generate-translet", Boolean.TRUE);
                tf.setAttribute("debug", Boolean.TRUE);
                tf.setAttribute("enable-inlining", Boolean.TRUE);
                tf.setAttribute("destination-directory", outputDirectory.getAbsolutePath());
                String transformFile = stylesheet.getName();
                transformFile = transformFile.replace('-', '_').substring(0, transformFile.length() - 5);
                log.info("compiling2 " + stylesheet + " " + transformFile);

                tf.setAttribute("translet-name", transformFile);

                Templates template = tf.newTemplates(new StreamSource(stylesheet.toURL().openStream()));

            } catch (Exception e) {
                log.error(stylesheet.getName() + " " + e.getMessage(), e);
                fail = true;
            }

        }
        if (fail) {
            throw new MojoFailureException("failed to compile stylesheets");
        }
    }
}
