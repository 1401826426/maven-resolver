/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.MetadataGenerator;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.metadata.Metadata;

/**
 */
final class Utils
{

    private static final Comparator<MetadataGeneratorFactory> COMPARATOR = new Comparator<MetadataGeneratorFactory>()
    {

        public int compare( MetadataGeneratorFactory o1, MetadataGeneratorFactory o2 )
        {
            return Float.compare( o2.getPriority(), o1.getPriority() );
        }

    };

    public static List<MetadataGeneratorFactory> sortMetadataGeneratorFactories( Collection<MetadataGeneratorFactory> factories )
    {
        List<MetadataGeneratorFactory> result = new ArrayList<MetadataGeneratorFactory>( factories );
        Collections.sort( result, COMPARATOR );
        return result;
    }

    public static List<Metadata> prepareMetadata( List<MetadataGenerator> generators, List<Artifact> artifacts )
    {
        List<Metadata> metadatas = new ArrayList<Metadata>();

        for ( MetadataGenerator generator : generators )
        {
            metadatas.addAll( generator.prepare( artifacts ) );
        }

        return metadatas;
    }

    public static List<Metadata> finishMetadata( List<MetadataGenerator> generators, List<Artifact> artifacts )
    {
        List<Metadata> metadatas = new ArrayList<Metadata>();

        for ( MetadataGenerator generator : generators )
        {
            metadatas.addAll( generator.finish( artifacts ) );
        }

        return metadatas;
    }

    public static <T> List<T> combine( Collection<? extends T> first, Collection<? extends T> second )
    {
        List<T> result = new ArrayList<T>( first.size() + second.size() );
        result.addAll( first );
        result.addAll( second );
        return result;
    }

}
