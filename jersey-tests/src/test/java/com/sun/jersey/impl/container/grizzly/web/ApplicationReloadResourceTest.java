/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.jersey.impl.container.grizzly.web;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import javax.ws.rs.Path;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.ContainerListener;
import com.sun.jersey.spi.container.ContainerNotifier;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.container.servlet.ServletContainer;
import com.sun.jersey.spi.inject.SingletonTypeInjectableProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

/**
 *
 * @author Paul.Sandoz@Sun.Com
 */
public class ApplicationReloadResourceTest extends AbstractGrizzlyWebContainerTester {

    public ApplicationReloadResourceTest(String testName) {
        super(testName);
    }

    @Path("/one")
    public static class A {

        @GET
        public String get() {
            return "one";
        }
    }

    @Path("/one")
    public static class B {

        @GET
        public String get() {
            return "two";
        }
    }

    private static class Reloader implements ContainerNotifier {

        List<ContainerListener> ls;

        public Reloader() {
            ls = new ArrayList<ContainerListener>();
        }

        public void addListener(ContainerListener l) {
            ls.add(l);
        }

        public void reload() {
            for (ContainerListener l : ls) {
                l.onReload();
            }
        }
    }

    @Path("reload")
    public static class ReloaderResource {

        @Context UriInfo ui;

        @Context Reloader r;

        public ReloaderResource(@Context HttpServletRequest r) {
            assertNotNull(r);
        }

        @POST
        public String postReload() {
            r.reload();
            return ui.getPath();
        }
    }

    public static class ReloadServletContainer extends ServletContainer {

        @Override
        protected void configure(final ServletConfig sc, ResourceConfig rc, WebApplication wa) {
            super.configure(sc, rc, wa);

            rc.getClasses().add(ReloaderResource.class);

            Reloader r = new Reloader();
            rc.getSingletons().add(new SingletonTypeInjectableProvider<Context, Reloader>(
                    Reloader.class, r){});

            rc.getProperties().put(ResourceConfig.PROPERTY_CONTAINER_NOTIFIER, r);
        }
    }

    public static class ResourceConfigApp extends DefaultResourceConfig {
        private static Class resource;

        public static void setResource(Class resource) {
            ResourceConfigApp.resource = resource;
        }
        
        public ResourceConfigApp() {
            getClasses().add(resource);
        }
    }

    public void testReload() {
        Map<String, String> initParams = new HashMap<String, String>();
        initParams.put(ServletContainer.RESOURCE_CONFIG_CLASS,
                ResourceConfigApp.class.getName());

        ResourceConfigApp.setResource(A.class);
        setServletClass(ReloadServletContainer.class);
        startServer(initParams);

        WebResource r = Client.create().resource(getUri().path("/").build());

        assertEquals("one", r.path("one").get(String.class));

        ResourceConfigApp.setResource(B.class);
        assertEquals("reload", r.path("reload").post(String.class));
        assertEquals("two", r.path("one").get(String.class));

        ResourceConfigApp.setResource(A.class);
        assertEquals("reload", r.path("reload").post(String.class));
        assertEquals("one", r.path("one").get(String.class));
    }
}
