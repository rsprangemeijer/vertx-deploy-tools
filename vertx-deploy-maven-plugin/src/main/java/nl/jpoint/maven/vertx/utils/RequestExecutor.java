package nl.jpoint.maven.vertx.utils;

import nl.jpoint.maven.vertx.mojo.DeployConfiguration;
import nl.jpoint.maven.vertx.request.DeployRequest;
import nl.jpoint.maven.vertx.request.Request;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RequestExecutor {

    private final Log log;
    private final long timeout;

    public RequestExecutor(Log log) {

        this.log = log;
        timeout = System.currentTimeMillis() + 60 * 5000;
    }

    private void executeAwsRequest(final HttpPost postRequest, final String host) throws MojoExecutionException, MojoFailureException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final String buildId;
            final AtomicInteger waitFor = new AtomicInteger(1);
            final AtomicInteger status = new AtomicInteger(0);
            try (CloseableHttpResponse response = httpClient.execute(postRequest)) {
                if (response.getStatusLine().getStatusCode() != 200) {
                    log.error("DeployModuleCommand : Post response status -> " + response.getStatusLine().getReasonPhrase());
                    throw new MojoExecutionException("Error deploying module. ");
                }

                buildId = EntityUtils.toString(response.getEntity());
            }

            ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();


            exec.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {

                    HttpGet get = new HttpGet(postRequest.getURI().getScheme()+"://"+postRequest.getURI().getHost() +":"+postRequest.getURI().getPort()+ "/deploy/status/" + buildId);
                    try (CloseableHttpResponse response = httpClient.execute(get)) {
                        int code = response.getStatusLine().getStatusCode();
                        String state = response.getStatusLine().getReasonPhrase();

                        switch (code) {
                            case 200:
                                log.info("Deploy request finished executing");
                                status.set(200);
                                waitFor.decrementAndGet();
                                break;
                            case 500:
                                status.set(500);
                                log.error("Deploy request failed");
                                waitFor.decrementAndGet();
                            default:
                                if (System.currentTimeMillis() > timeout) {
                                    status.set(500);
                                    log.error("Timeout while waiting for deploy reques.");
                                    waitFor.decrementAndGet();
                                }
                                log.info("Waiting for deploy to finish. Current status : " + state);
                        }

                    } catch (IOException e) {
                        status.set(500);
                        waitFor.decrementAndGet();
                    }
                }
            }, 0, 15, TimeUnit.SECONDS);

            while (waitFor.intValue() != 0) {
                log.info("timeout while waiting for deploy request");
                Thread.sleep(timeout);
            }

            exec.shutdown();
            exec.awaitTermination(30, TimeUnit.SECONDS);

            if (status.get() != 200) {
                throw new MojoFailureException("Error deploying module.");
            }


        } catch (IOException e) {
            log.error("testDeployModuleCommand ", e);
            throw new MojoExecutionException("Error deploying module.", e);
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private void executeRequest(HttpPost postRequest) throws MojoExecutionException {

        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                log.info("Waiting for deploy request to return...");
            }
        }, 5, 5, TimeUnit.SECONDS);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse response = httpClient.execute(postRequest)) {
                exec.shutdown();
                log.info("DeployModuleCommand : Post response status code -> " + response.getStatusLine().getStatusCode());

                if (response.getStatusLine().getStatusCode() != 200) {
                    log.error("DeployModuleCommand : Post response status -> " + response.getStatusLine().getReasonPhrase());
                    throw new MojoExecutionException("Error deploying module. ");
                }
            } catch (IOException e) {
                log.error("testDeployModuleCommand ", e);
                throw new MojoExecutionException("Error deploying module.", e);
            }

        } catch (IOException e) {
            log.error("testDeployModuleCommand ", e);
            throw new MojoExecutionException("Error deploying module.", e);
        } finally {
            if (!exec.isShutdown()) {
                log.info("Shutdown executor after error");
                exec.shutdown();
            }
        }
    }

    public void executeSingleDeployRequest(DeployConfiguration activeConfiguration, Request request) throws MojoExecutionException {
        for (String host : activeConfiguration.getHosts()) {
            HttpPost post = new HttpPost(host + request.getEndpoint());

            ByteArrayInputStream bos = new ByteArrayInputStream(request.toJson().getBytes());
            BasicHttpEntity entity = new BasicHttpEntity();
            entity.setContent(bos);
            entity.setContentLength(request.toJson().getBytes().length);
            post.setEntity(entity);

            this.executeRequest(post);
        }
    }

    public void executeDeployRequests(DeployConfiguration activeConfiguration, DeployRequest deployRequest) throws MojoExecutionException, MojoFailureException {

        for (String host : activeConfiguration.getHosts()) {

            log.info("Deploying to host : " + host);

            HttpPost post = new HttpPost(host + deployRequest.getEndpoint());
            ByteArrayInputStream bos = new ByteArrayInputStream(deployRequest.toJson().getBytes());
            BasicHttpEntity entity = new BasicHttpEntity();
            entity.setContent(bos);
            entity.setContentLength(deployRequest.toJson().getBytes().length);
            post.setEntity(entity);
            if (!activeConfiguration.getAws()) {
                this.executeRequest(post);
            } else {
                this.executeAwsRequest(post, host);
            }

        }
    }

}
