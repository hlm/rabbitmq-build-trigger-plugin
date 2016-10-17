package org.jenkinsci.plugins.rabbitmqbuildtrigger.workflow;

import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishChannel;
import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishChannelFactory;
import org.jenkinsci.plugins.rabbitmqconsumer.publishers.PublishResult;

import com.rabbitmq.client.AMQP.BasicProperties;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.rabbitmqbuildtrigger.RemoteBuildTrigger;
import org.jenkinsci.plugins.rabbitmqbuildtrigger.Messages;

/**
 * Workflow step to send a Slack channel notification.
 */
public class RabbitMQSendStep extends AbstractStepImpl {

    private @Nonnull String message;
    private @Nonnull String exchange;
    private String routingKey;

    private static final String HEADER_JENKINS_URL = "jenkins-url";
    private static final String JSON_CONTENT_TYPE = "application/json";

    @Nonnull
    public String getMessage() {
        return message;
    }

    @DataBoundSetter
    public void setMessage(String message) {
        this.message = Util.fixEmpty(message);
    }

    @Nonnull
    public String getExchange() {
        return exchange;
    }

    @DataBoundSetter
    public void setExchange(String exchange) {
        this.exchange = Util.fixEmpty(exchange);
    }

    public String getRoutingKey() {
        return routingKey;
    }

    @DataBoundSetter
    public void setRoutingKey(String routingKey) {
        this.routingKey = Util.fixEmpty(routingKey);
    }

    @DataBoundConstructor
    public RabbitMQSendStep(@Nonnull String message) {
        this.message = message;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(RabbitMQSendStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "rabbitMQSend";
        }

        @Override
        public String getDisplayName() {
            return Messages.RabbitMQSendStepDisplayName();
        }
    }

    public static class RabbitMQSendStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        @Inject
        transient RabbitMQSendStep step;

        @StepContextParameter
        transient TaskListener listener;

        @Override
        protected Void run() throws Exception {

            //default to global config values if not set in step, but allow step to override all global settings
            Jenkins jenkins;
            listener.getLogger().println(Messages.RabbitMQSendStepConfig(step.exchange, step.routingKey, step.message));

           //Jenkins.getInstance() may return null, no message sent in that case
            try {
                jenkins = Jenkins.getInstance();
         
                // Basic property
                BasicProperties.Builder builder = new BasicProperties.Builder();
                builder.appId(RemoteBuildTrigger.PLUGIN_APPID);
                builder.contentType(JSON_CONTENT_TYPE);

                // Header
                Map<String, Object> headers = new HashMap<String, Object>();
                headers.put(HEADER_JENKINS_URL, Jenkins.getInstance().getRootUrl());
                builder.headers(headers);

                // Publish message
                PublishChannel ch = PublishChannelFactory.getPublishChannel();
                if (ch != null && ch.isOpen()) {
                    if (step.exchange != null) {
                        // return value is not needed if you don't need to wait.
                        //Future<PublishResult> future = ch.publish(exchange, routingKey, builder.build(), json.toString().getBytes());
                        ch.publish(step.exchange, step.routingKey, builder.build(), step.message.getBytes());
                    }
                }
            } catch (NullPointerException ne) {
                listener.error(ne.getMessage());
                return null;
            }

            return null;
        }

    }

}
