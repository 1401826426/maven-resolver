/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.transport.http;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.params.AuthParams;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DecompressingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.spi.connector.transport.TransportTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.util.ConfigUtils;

/**
 * A transporter for HTTP/HTTPS.
 */
final class HttpTransporter
    implements Transporter
{

    private static final Pattern CONTENT_RANGE_PATTERN =
        Pattern.compile( "\\s*bytes\\s+([0-9]+)\\s*-\\s*([0-9]+)\\s*/.*" );

    private final AuthenticationContext repoAuthContext;

    private final AuthenticationContext proxyAuthContext;

    private final AtomicBoolean closed;

    private final URI baseUri;

    private final HttpHost server;

    private final HttpHost proxy;

    private final HttpClient client;

    private final Map<?, ?> headers;

    private final LocalState state;

    public HttpTransporter( RemoteRepository repository, RepositorySystemSession session, Logger logger )
        throws NoTransporterException
    {
        if ( !"http".equalsIgnoreCase( repository.getProtocol() )
            && !"https".equalsIgnoreCase( repository.getProtocol() ) )
        {
            throw new NoTransporterException( repository );
        }
        try
        {
            baseUri = new URI( repository.getUrl() );
            if ( baseUri.isOpaque() )
            {
                throw new URISyntaxException( repository.getUrl(), "URL must not be opaque" );
            }
            server = URIUtils.extractHost( baseUri );
            if ( server == null )
            {
                throw new URISyntaxException( repository.getUrl(), "URL lacks host name" );
            }
        }
        catch ( URISyntaxException e )
        {
            throw new NoTransporterException( repository, e );
        }
        proxy = toHost( repository.getProxy() );

        repoAuthContext = AuthenticationContext.forRepository( session, repository );
        proxyAuthContext = AuthenticationContext.forProxy( session, repository );
        closed = new AtomicBoolean();

        state = new LocalState( session, repository, new SslConfig( session, repoAuthContext ) );

        headers =
            ConfigUtils.getMap( session, Collections.emptyMap(), ConfigurationProperties.HTTP_HEADERS + "."
                + repository.getId(), ConfigurationProperties.HTTP_HEADERS );

        DefaultHttpClient client = new DefaultHttpClient( state.getConnectionManager() );

        configureClient( client.getParams(), session, repository, proxy );

        DeferredCredentialsProvider credsProvider = new DeferredCredentialsProvider();
        addCredentials( credsProvider, server.getHostName(), AuthScope.ANY_PORT, repoAuthContext );
        if ( proxy != null )
        {
            addCredentials( credsProvider, proxy.getHostName(), proxy.getPort(), proxyAuthContext );
        }
        client.setCredentialsProvider( credsProvider );

        this.client = new DecompressingHttpClient( client );
    }

    private static HttpHost toHost( Proxy proxy )
    {
        HttpHost host = null;
        if ( proxy != null )
        {
            host = new HttpHost( proxy.getHost(), proxy.getPort() );
        }
        return host;
    }

    private static void configureClient( HttpParams params, RepositorySystemSession session,
                                         RemoteRepository repository, HttpHost proxy )
    {
        AuthParams.setCredentialCharset( params,
                                         ConfigUtils.getString( session,
                                                                ConfigurationProperties.DEFAULT_HTTP_CREDENTIAL_ENCODING,
                                                                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING + "."
                                                                    + repository.getId(),
                                                                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING ) );
        ConnRouteParams.setDefaultProxy( params, proxy );
        HttpConnectionParams.setConnectionTimeout( params,
                                                   ConfigUtils.getInteger( session,
                                                                           ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT,
                                                                           ConfigurationProperties.CONNECT_TIMEOUT
                                                                               + "." + repository.getId(),
                                                                           ConfigurationProperties.CONNECT_TIMEOUT ) );
        HttpConnectionParams.setSoTimeout( params,
                                           ConfigUtils.getInteger( session,
                                                                   ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT,
                                                                   ConfigurationProperties.REQUEST_TIMEOUT + "."
                                                                       + repository.getId(),
                                                                   ConfigurationProperties.REQUEST_TIMEOUT ) );
        HttpProtocolParams.setUserAgent( params, ConfigUtils.getString( session,
                                                                        ConfigurationProperties.DEFAULT_USER_AGENT,
                                                                        ConfigurationProperties.USER_AGENT ) );
    }

    private static void addCredentials( DeferredCredentialsProvider provider, String host, int port,
                                        AuthenticationContext ctx )
    {
        if ( ctx != null )
        {
            AuthScope basicScope = new AuthScope( host, port );
            provider.setCredentials( basicScope, new DeferredCredentialsProvider.BasicFactory( ctx ) );

            AuthScope ntlmScope = new AuthScope( host, port, AuthScope.ANY_REALM, "ntlm" );
            provider.setCredentials( ntlmScope, new DeferredCredentialsProvider.NtlmFactory( ctx ) );
        }
    }

    private URI resolve( TransportTask task )
    {
        return resolve( baseUri, task.getLocation() );
    }

    static URI resolve( URI base, URI ref )
    {
        String path = ref.getRawPath();
        if ( path != null && path.length() > 0 )
        {
            path = base.getRawPath();
            if ( path == null || !path.endsWith( "/" ) )
            {
                try
                {
                    base = new URI( base.getScheme(), base.getAuthority(), base.getPath() + '/', null, null );
                }
                catch ( URISyntaxException e )
                {
                    throw new IllegalStateException( e );
                }
            }
        }
        return URIUtils.resolve( base, ref );
    }

    public int classify( Throwable error )
    {
        if ( error instanceof HttpResponseException
            && ( (HttpResponseException) error ).getStatusCode() == HttpStatus.SC_NOT_FOUND )
        {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    public void peek( PeekTask task )
        throws Exception
    {
        failIfClosed( task );

        HttpHead request = commonHeaders( new HttpHead( resolve( task ) ) );
        execute( request, null );
    }

    public void get( GetTask task )
        throws Exception
    {
        failIfClosed( task );

        EntityGetter getter = new EntityGetter( task );
        HttpGet request = commonHeaders( new HttpGet( resolve( task ) ) );
        resume( request, task );
        try
        {
            execute( request, getter );
        }
        catch ( HttpResponseException e )
        {
            if ( e.getStatusCode() == HttpStatus.SC_PRECONDITION_FAILED && request.containsHeader( HttpHeaders.RANGE ) )
            {
                request = commonHeaders( new HttpGet( request.getURI() ) );
                execute( request, getter );
                return;
            }
            throw e;
        }
    }

    public void put( PutTask task )
        throws Exception
    {
        failIfClosed( task );

        PutTaskEntity entity = new PutTaskEntity( task );
        HttpPut request = commonHeaders( expectContinue( new HttpPut( resolve( task ) ) ) );
        request.setEntity( entity );
        try
        {
            execute( request, null );
        }
        catch ( HttpResponseException e )
        {
            if ( e.getStatusCode() == HttpStatus.SC_EXPECTATION_FAILED && request.containsHeader( HttpHeaders.EXPECT ) )
            {
                state.setExpectContinue( false );
                request = commonHeaders( new HttpPut( request.getURI() ) );
                request.setEntity( entity );
                execute( request, null );
                return;
            }
            throw e;
        }
    }

    private void execute( HttpUriRequest request, EntityGetter getter )
        throws Exception
    {
        try
        {
            SharingHttpContext context = new SharingHttpContext( state );
            HttpResponse response = client.execute( server, request, context );
            try
            {
                context.close();
                handleStatus( response );
                if ( getter != null )
                {
                    getter.handle( response );
                }
            }
            finally
            {
                EntityUtils.consumeQuietly( response.getEntity() );
            }
        }
        catch ( IOException e )
        {
            if ( e.getCause() instanceof TransferCancelledException )
            {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    private <T extends HttpUriRequest> T commonHeaders( T request )
    {
        request.setHeader( HttpHeaders.CACHE_CONTROL, "no-cache, no-store" );
        request.setHeader( HttpHeaders.PRAGMA, "no-cache" );
        for ( Map.Entry<?, ?> entry : headers.entrySet() )
        {
            if ( !( entry.getKey() instanceof String ) )
            {
                continue;
            }
            if ( entry.getValue() instanceof String )
            {
                request.setHeader( entry.getKey().toString(), entry.getValue().toString() );
            }
            else
            {
                request.removeHeaders( entry.getKey().toString() );
            }
        }
        return request;
    }

    private <T extends HttpUriRequest> T resume( T request, GetTask task )
    {
        long resumeOffset = task.getResumeOffset();
        if ( resumeOffset > 0 && task.getDataFile() != null )
        {
            request.setHeader( HttpHeaders.RANGE, "bytes=" + Long.toString( resumeOffset ) + '-' );
            request.setHeader( HttpHeaders.IF_UNMODIFIED_SINCE,
                               DateUtils.formatDate( new Date( task.getDataFile().lastModified() - 60 * 1000 ) ) );
            request.setHeader( HttpHeaders.ACCEPT_ENCODING, "identity" );
        }
        return request;
    }

    private <T extends HttpUriRequest> T expectContinue( T request )
    {
        if ( state.isExpectContinue() )
        {
            request.setHeader( HttpHeaders.EXPECT, "100-continue" );
        }
        return request;
    }

    private void handleStatus( HttpResponse response )
        throws HttpResponseException
    {
        int status = response.getStatusLine().getStatusCode();
        if ( status >= 300 )
        {
            throw new HttpResponseException( status, response.getStatusLine().getReasonPhrase() + " (" + status + ")" );
        }
    }

    private static void copy( OutputStream os, InputStream is, TransportListener listener )
        throws IOException, TransferCancelledException
    {
        ByteBuffer buffer = ByteBuffer.allocate( 1024 * 32 );
        byte[] array = buffer.array();
        for ( int read = is.read( array ); read >= 0; read = is.read( array ) )
        {
            os.write( array, 0, read );
            buffer.rewind();
            buffer.limit( read );
            listener.transportProgressed( buffer );
        }
    }

    private static void close( Closeable file )
    {
        if ( file != null )
        {
            try
            {
                file.close();
            }
            catch ( IOException e )
            {
                // irrelevant
            }
        }
    }

    private void failIfClosed( TransportTask task )
    {
        if ( closed.get() )
        {
            throw new IllegalStateException( "transporter closed, cannot execute task " + task );
        }
    }

    public void close()
    {
        if ( closed.compareAndSet( false, true ) )
        {
            AuthenticationContext.close( repoAuthContext );
            AuthenticationContext.close( proxyAuthContext );

            state.close();
        }
    }

    private class EntityGetter
    {

        private final GetTask task;

        public EntityGetter( GetTask task )
        {
            this.task = task;
        }

        public void handle( HttpResponse response )
            throws IOException, TransferCancelledException
        {
            HttpEntity entity = response.getEntity();
            if ( entity == null )
            {
                entity = new ByteArrayEntity( new byte[0] );
            }

            long offset = 0, length = entity.getContentLength();
            Header range = response.getFirstHeader( HttpHeaders.CONTENT_RANGE );
            if ( range != null && range.getValue() != null )
            {
                Matcher m = CONTENT_RANGE_PATTERN.matcher( range.getValue() );
                if ( !m.matches() )
                {
                    throw new IOException( "Invalid Content-Range header for partial download: " + range.getValue() );
                }
                offset = Long.parseLong( m.group( 1 ) );
                length = Long.parseLong( m.group( 2 ) ) + 1;
                if ( offset < 0 || offset >= length || offset != task.getResumeOffset() )
                {
                    throw new IOException( "Invalid Content-Range header for partial download: " + range.getValue() );
                }
            }

            InputStream is = entity.getContent();
            try
            {
                task.getListener().transportStarted( offset, length );
                OutputStream os = task.newOutputStream( offset > 0 );
                try
                {
                    copy( os, is, task.getListener() );
                    os.close();
                }
                finally
                {
                    close( os );
                }
            }
            finally
            {
                close( is );
            }
        }

    }

    private class PutTaskEntity
        extends AbstractHttpEntity
    {

        private final PutTask task;

        public PutTaskEntity( PutTask task )
        {
            this.task = task;
        }

        public boolean isRepeatable()
        {
            return true;
        }

        public boolean isStreaming()
        {
            return false;
        }

        public long getContentLength()
        {
            return task.getDataLength();
        }

        public InputStream getContent()
            throws IOException
        {
            return task.newInputStream();
        }

        public void writeTo( OutputStream os )
            throws IOException
        {
            try
            {
                task.getListener().transportStarted( 0, task.getDataLength() );
                InputStream is = task.newInputStream();
                try
                {
                    copy( os, is, task.getListener() );
                    os.flush();
                }
                finally
                {
                    close( is );
                }
            }
            catch ( TransferCancelledException e )
            {
                throw (IOException) new InterruptedIOException().initCause( e );
            }
        }

    }

}
