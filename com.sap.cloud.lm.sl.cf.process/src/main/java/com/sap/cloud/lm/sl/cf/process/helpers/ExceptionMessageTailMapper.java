package com.sap.cloud.lm.sl.cf.process.helpers;

import java.text.MessageFormat;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import com.sap.cloud.lm.sl.cf.core.util.ApplicationConfiguration;
import com.sap.cloud.lm.sl.cf.process.message.Messages;

public class ExceptionMessageTailMapper {

    public enum CloudComponents {
        CLOUD_CONTROLLER("cloud-controller"), DEPLOY_SERVICE("deploy-service"), SERVICE_BROKERS("service-brokers");
        private String name;

        private CloudComponents(String name) {
            this.name = name;
        }
    }

    private static String mapServiceBrokerComponent(Map<String, String> serviceBrokersComponents, String supportChannel, String offering) {

        for (Entry<String, String> entry : serviceBrokersComponents.entrySet()) {
            if (entry.getKey()
                     .equals(offering)) {
                return MessageFormat.format(Messages.CREATE_SUPPORT_TICKET_TO_COMPONENT, supportChannel, entry.getValue());
            }
        }

        return Messages.CREATE_SUPPORT_TICKET_GENERIC_MESSAGE;
    }

    @SuppressWarnings("unchecked")
    public static String map(ApplicationConfiguration configuration, CloudComponents cloudComponent, String serviceName, String offering) {

        if (!configuration.isInternalEnvironment()) {
            return mapExternalMessages(cloudComponent, serviceName);
        }

        Map<String, Object> cloudComponents = configuration.getCloudComponents();
        String supportChannel = configuration.getInternalSupportChannel();

        if (cloudComponent == CloudComponents.DEPLOY_SERVICE) {
            return messageResolver(Messages.CREATE_SUPPORT_TICKET_TO_DS_COMPONENT,
                                   MessageFormat.format(Messages.CREATE_SUPPORT_TICKET_TO_COMPONENT, supportChannel,
                                                        cloudComponents.get(CloudComponents.DEPLOY_SERVICE.name)));
        }

        if (cloudComponent == CloudComponents.CLOUD_CONTROLLER) {
            return messageResolver(Messages.CREATE_SUPPORT_TICKET_TO_CC_COMPONENT,
                                   MessageFormat.format(Messages.CREATE_SUPPORT_TICKET_TO_COMPONENT, supportChannel,
                                                        cloudComponents.get(CloudComponents.CLOUD_CONTROLLER.name)));
        }

        if (cloudComponent == CloudComponents.SERVICE_BROKERS) {
            return mapServiceBrokerComponent((Map<String, String>) cloudComponents.get(CloudComponents.SERVICE_BROKERS.name),
                                             supportChannel, offering);
        }

        return StringUtils.EMPTY;
    }

    private static String mapExternalMessages(CloudComponents cloudComponent, String serviceName) {
        if (cloudComponent == CloudComponents.SERVICE_BROKERS) {
            return MessageFormat.format(Messages.CREATE_SUPPORT_TICKET_TO_SERVICE_BROKER_GENERIC_MESSAGE, serviceName);
        }

        if (cloudComponent == CloudComponents.CLOUD_CONTROLLER) {
            return Messages.CREATE_SUPPORT_TICKET_TO_CC_COMPONENT;
        }

        return Messages.CREATE_SUPPORT_TICKET_TO_DS_COMPONENT;
    }

    private static String messageResolver(String... arguments) {
        String pattern = "{0}: {1}";
        return MessageFormat.format(pattern, arguments[0], arguments[1]);
    }

}