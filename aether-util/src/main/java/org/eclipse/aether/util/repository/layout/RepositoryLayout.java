/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.repository.layout;

import java.net.URI;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * The layout for a remote repository whose artifacts/metadata can be addressed via URIs.
 * 
 * @deprecated Repository connectors should use the
 *             {@code org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider} instead.
 */
@Deprecated
public interface RepositoryLayout
{

    /**
     * Gets the URI to the location within a remote repository where the specified artifact would be stored. The URI is
     * relative to the root directory of the repository.
     * 
     * @param artifact The artifact to get the URI for, must not be {@code null}.
     * @return The relative URI to the artifact, never {@code null}.
     */
    URI getPath( Artifact artifact );

    /**
     * Gets the URI to the location within a remote repository where the specified metadata would be stored. The URI is
     * relative to the root directory of the repository.
     * 
     * @param metadata The metadata to get the URI for, must not be {@code null}.
     * @return The relative URI to the metadata, never {@code null}.
     */
    URI getPath( Metadata metadata );

}
