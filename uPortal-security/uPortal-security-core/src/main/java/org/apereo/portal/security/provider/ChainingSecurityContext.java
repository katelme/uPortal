/**
 * Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Apereo
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at the
 * following location:
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.security.provider;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.Vector;
import org.apereo.portal.properties.PropertiesManager;
import org.apereo.portal.security.IAdditionalDescriptor;
import org.apereo.portal.security.IOpaqueCredentials;
import org.apereo.portal.security.IParentAwareSecurityContext;
import org.apereo.portal.security.IPrincipal;
import org.apereo.portal.security.ISecurityContext;
import org.apereo.portal.security.PortalSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the basic abstract class for all security contexts that should chain to children security
 * contexts.
 *
 * <p>returns an Enumeration of names for the subcontexts.
 */
public abstract class ChainingSecurityContext implements ISecurityContext {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Default value for stopWhenAuthenticated. This value will be used when the corresponding
     * property cannot be loaded.
     */
    private static final boolean DEFAULT_STOP_WHEN_AUTHENTICATED = true;

    protected static boolean stopWhenAuthenticated =
            PropertiesManager.getPropertyAsBoolean(
                    "org.apereo.portal.security.provider.ChainingSecurityContext.stopWhenAuthenticated",
                    DEFAULT_STOP_WHEN_AUTHENTICATED);

    protected boolean isauth = false;
    protected Vector mySubContexts;
    protected ChainingPrincipal myPrincipal;
    protected ChainingOpaqueCredentials myOpaqueCredentials;
    protected IAdditionalDescriptor myAdditionalDescriptor;

    public ChainingSecurityContext() {
        myPrincipal = new ChainingPrincipal();
        myOpaqueCredentials = new ChainingOpaqueCredentials();
        myAdditionalDescriptor = new ChainingAdditionalDescriptor();
        mySubContexts = new Vector();
    }

    @Override
    public IPrincipal getPrincipalInstance() {
        if (this.isauth) return new ChainingPrincipal();
        else return this.myPrincipal;
    }

    @Override
    public IOpaqueCredentials getOpaqueCredentialsInstance() {
        if (this.isauth) return new ChainingOpaqueCredentials();
        else return this.myOpaqueCredentials;
    }

    /**
     * We walk the chain of subcontexts assigning principals and opaqueCredentials from the parent.
     * Note that the contexts themselves should resist actually performing the assignment if an
     * assignment has already been made to either the credentials or the UID.
     */
    @Override
    public synchronized void authenticate() throws PortalSecurityException {
        int i;
        Enumeration e = mySubContexts.elements();

        while (e.hasMoreElements()) {
            ISecurityContext sctx = ((Entry) e.nextElement()).getCtx();
            // The principal and credential are now set for all subcontexts in Authentication
            try {
                if (sctx instanceof IParentAwareSecurityContext) {
                    ((IParentAwareSecurityContext) sctx).authenticate(this);
                } else {
                    sctx.authenticate();
                }
            } catch (Exception ex) {
                log.error("Exception authenticating subcontext " + sctx, ex);
            }
            // Stop attempting to authenticate if authenticated and if the property flag is set
            if (stopWhenAuthenticated && sctx.isAuthenticated()) {
                break;
            }
        }

        // Zero out the actual credentials if it isn't already null
        if (this.myOpaqueCredentials.credentialstring != null) {
            for (i = 0; i < this.myOpaqueCredentials.credentialstring.length; i++)
                this.myOpaqueCredentials.credentialstring[i] = 0;
            myOpaqueCredentials.credentialstring = null;
        }

        return;
    }

    @Override
    public IPrincipal getPrincipal() {
        if (this.isauth) return this.myPrincipal;
        else return null;
    }

    @Override
    public IOpaqueCredentials getOpaqueCredentials() {
        if (this.isauth) return this.myOpaqueCredentials;
        else return null;
    }

    @Override
    public IAdditionalDescriptor getAdditionalDescriptor() {
        if (this.isauth) return this.myAdditionalDescriptor;
        else return null;
    }

    @Override
    public boolean isAuthenticated() {
        return this.isauth;
    }

    @Override
    public synchronized ISecurityContext getSubContext(String name) {
        for (int i = 0; i < mySubContexts.size(); i++) {
            Entry entry = (Entry) mySubContexts.get(i);
            if (entry.getKey() != null && entry.getKey().equals(name)) {
                return (entry.getCtx());
            }
        }
        PortalSecurityException ep = new PortalSecurityException("No such subcontext: " + name);
        if (log.isDebugEnabled()) log.debug("No such subcontext as " + name, ep);
        return (null);
    }

    public synchronized boolean doesSubContextExist(String name) {
        for (int i = 0; i < mySubContexts.size(); i++) {
            Entry entry = (Entry) mySubContexts.get(i);
            if (entry.getKey() != null && entry.getKey().equals(name)) {
                return (true);
            }
        }
        return (false);
    }

    // Return an enumeration of subcontexts by running the vector and
    // creating the enumeration.  All this so the subcontexts will
    // be returned in the order they appeared in the properties file.
    @Override
    public synchronized Enumeration getSubContexts() {
        Enumeration e = mySubContexts.elements();
        class Adapter implements Enumeration {
            Enumeration base;

            public Adapter(Enumeration e) {
                this.base = e;
            }

            @Override
            public boolean hasMoreElements() {
                return base.hasMoreElements();
            }

            @Override
            public Object nextElement() {
                return ((Entry) base.nextElement()).getCtx();
            }
        }
        return new Adapter(e);
    }

    @Override
    public synchronized void addSubContext(String name, ISecurityContext ctx)
            throws PortalSecurityException {
        // Make sure the subcontext does not already exist in the chain
        if (doesSubContextExist(name)) {
            PortalSecurityException ep =
                    new PortalSecurityException("Subcontext already exists: " + name);
            log.error("Subcontext already exists:" + name, ep);
            throw (ep);
        } else {
            mySubContexts.add(new Entry(name, ctx));
        }
    }

    // I suppose the public class could just implement all of these interfaces
    // but I prefer member classes. -ADN

    protected static class ChainingPrincipal implements IPrincipal {
        protected String globalUID;
        protected String UID;
        protected String FullName;

        /** Original, no-arg constructor used by the <code>ChainingSecurityContext</code>. */
        public ChainingPrincipal() {}

        /**
         * Creates a new <code>ChainingPrincipal</code> from the specified <code>IPrincipal</code>.
         */
        public ChainingPrincipal(IPrincipal p) {
            this.globalUID = p.getGlobalUID();
            this.UID = p.getUID();
            this.FullName = p.getFullName();
        }

        @Override
        public String getUID() {
            return this.UID;
        }

        @Override
        public String getGlobalUID() {
            return this.globalUID;
        }

        // This is supposed to be the person's "human readable" name. We should
        // probably do an account lookup at the very least to return this.

        @Override
        public String getFullName() {
            return this.FullName;
        }

        @Override
        public void setUID(String UID) {
            if (this.UID == null) this.UID = UID;
        }

        public void setFullName(String FullName) {
            if (this.FullName == null) this.FullName = FullName;
        }
    }

    protected static class ChainingOpaqueCredentials implements IOpaqueCredentials {

        public byte[] credentialstring;

        // Since we want to explicitly zero our credentials after authenticate,
        // copy the credentials here in case a sub-authenticator doesn't want
        // to perform the operation immediately.

        @Override
        public void setCredentials(byte[] credentials) {
            int i;

            if (this.credentialstring == null) {
                this.credentialstring = new byte[credentials.length];
                for (i = 0; i < credentials.length; i++) this.credentialstring[i] = credentials[i];
            }
        }

        @Override
        public void setCredentials(String credentials) {
            if (this.credentialstring == null && credentials != null)
                setCredentials(credentials.getBytes());
        }
    }

    // Returns an Enumeration of the names of the subcontexts.
    @Override
    public synchronized Enumeration getSubContextNames() {
        Vector scNames = new Vector();
        for (int i = 0; i < mySubContexts.size(); i++) {
            Entry entry = (Entry) mySubContexts.get(i);
            if (entry.getKey() != null) {
                scNames.add(entry.getKey());
            }
        }
        return scNames.elements();
    }

    /**
     * A default, placeholder implementation of IAdditionalDescriptor an instance of which is the
     * default value for the instance variable "myAdditionalDescriptor" of instances of this class.
     */
    public static class ChainingAdditionalDescriptor implements IAdditionalDescriptor {
        // do nothing
    }

    // entries in our subcontext list
    private static class Entry implements Serializable {
        String key;
        ISecurityContext ctx;

        public Entry(String key, ISecurityContext ctx) {
            this.key = key;
            this.ctx = ctx;
        }

        public ISecurityContext getCtx() {
            return this.ctx;
        }

        public String getKey() {
            return this.key;
        }
    }
}
