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
package org.eclipse.aether.util.artifact;

import static org.junit.Assert.*;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.artifact.DefaultArtifact;
import org.junit.Test;

/**
 */
public class ArtifactIdUtilsTest
{

    @Test
    public void testToIdArtifact()
    {
        Artifact artifact = null;
        assertSame( null, ArtifactIdUtils.toId( artifact ) );

        artifact = new DefaultArtifact( "gid", "aid", "ext", "1.0-20110205.132618-23" );
        assertEquals( "gid:aid:ext:1.0-20110205.132618-23", ArtifactIdUtils.toId( artifact ) );

        artifact = new DefaultArtifact( "gid", "aid", "cls", "ext", "1.0-20110205.132618-23" );
        assertEquals( "gid:aid:ext:cls:1.0-20110205.132618-23", ArtifactIdUtils.toId( artifact ) );
    }

    @Test
    public void testToIdStrings()
    {
        assertEquals( ":::", ArtifactIdUtils.toId( null, null, null, null, null ) );

        assertEquals( "gid:aid:ext:1", ArtifactIdUtils.toId( "gid", "aid", "ext", "", "1" ) );

        assertEquals( "gid:aid:ext:cls:1", ArtifactIdUtils.toId( "gid", "aid", "ext", "cls", "1" ) );
    }

    @Test
    public void testToBaseIdArtifact()
    {
        Artifact artifact = null;
        assertSame( null, ArtifactIdUtils.toBaseId( artifact ) );

        artifact = new DefaultArtifact( "gid", "aid", "ext", "1.0-20110205.132618-23" );
        assertEquals( "gid:aid:ext:1.0-SNAPSHOT", ArtifactIdUtils.toBaseId( artifact ) );

        artifact = new DefaultArtifact( "gid", "aid", "cls", "ext", "1.0-20110205.132618-23" );
        assertEquals( "gid:aid:ext:cls:1.0-SNAPSHOT", ArtifactIdUtils.toBaseId( artifact ) );
    }

    @Test
    public void testToVersionlessIdArtifact()
    {
        Artifact artifact = null;
        assertSame( null, ArtifactIdUtils.toId( artifact ) );

        artifact = new DefaultArtifact( "gid", "aid", "ext", "1" );
        assertEquals( "gid:aid:ext", ArtifactIdUtils.toVersionlessId( artifact ) );

        artifact = new DefaultArtifact( "gid", "aid", "cls", "ext", "1" );
        assertEquals( "gid:aid:ext:cls", ArtifactIdUtils.toVersionlessId( artifact ) );
    }

    @Test
    public void testToVersionlessIdStrings()
    {
        assertEquals( "::", ArtifactIdUtils.toVersionlessId( null, null, null, null ) );

        assertEquals( "gid:aid:ext", ArtifactIdUtils.toVersionlessId( "gid", "aid", "ext", "" ) );

        assertEquals( "gid:aid:ext:cls", ArtifactIdUtils.toVersionlessId( "gid", "aid", "ext", "cls" ) );
    }

}
