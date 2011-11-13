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
package org.eclipse.aether.connector.file;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLogger;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;

/**
 * Factory creating {@link FileRepositoryConnector}s.
 */
@Component( role = RepositoryConnectorFactory.class, hint = "file" )
public class FileRepositoryConnectorFactory
    implements RepositoryConnectorFactory, Service
{

    @Requirement( role = LoggerFactory.class )
    private Logger logger = NullLogger.INSTANCE;

    @Requirement
    private FileProcessor fileProcessor;

    private float priority = 1;

    public static final String CFG_PREFIX = "aether.connector.file";

    public FileRepositoryConnectorFactory()
    {
        // enables default constructor
    }

    public FileRepositoryConnectorFactory( FileProcessor fileProcessor )
    {
        setFileProcessor( fileProcessor );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        setFileProcessor( locator.getService( FileProcessor.class ) );
    }

    /**
     * Sets the logger factory to use for this component.
     * 
     * @param loggerFactory The logger factory to use, may be {@code null} to disable logging.
     * @return This component for chaining, never {@code null}.
     */
    public FileRepositoryConnectorFactory setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLogger.getIfNull( loggerFactory, FileRepositoryConnector.class );
        return this;
    }

    void setLogger( LoggerFactory loggerFactory )
    {
        // plexus support
        setLoggerFactory( loggerFactory );
    }

    /**
     * Sets the file processor to use for this component.
     * 
     * @param fileProcessor The file processor to use, must not be {@code null}.
     * @return This component for chaining, never {@code null}.
     */
    public FileRepositoryConnectorFactory setFileProcessor( FileProcessor fileProcessor )
    {
        if ( fileProcessor == null )
        {
            throw new IllegalArgumentException( "file processor has not been specified" );
        }
        this.fileProcessor = fileProcessor;
        return this;
    }

    public RepositoryConnector newInstance( RepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryConnectorException
    {
        if ( "file".equalsIgnoreCase( repository.getProtocol() ) )
        {
            FileRepositoryConnector connector =
                new FileRepositoryConnector( session, repository, fileProcessor, logger );
            return connector;
        }

        throw new NoRepositoryConnectorException( repository );
    }

    public float getPriority()
    {
        return priority;
    }

    /**
     * Sets the priority of this component.
     * 
     * @param priority The priority.
     * @return This component for chaining, never {@code null}.
     */
    public FileRepositoryConnectorFactory setPriority( float priority )
    {
        this.priority = priority;
        return this;
    }

}
