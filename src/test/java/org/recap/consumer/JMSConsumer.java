package org.recap.consumer;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

/**
 * Created by sudhishk on 17/1/17.
 */
public class JMSConsumer implements MessageListener {

    private final static Logger logger = LoggerFactory.getLogger(JMSConsumer.class);

    private static String topicName = "PUL.RequestT";
    private static String queueName = "lasOutgoingQ";
    private static String tstBrokerURL = "tcp://tst-recap-direct.htcinc.com:61616";


    public static void main(String[] args) throws Exception {
        topicQueueLasOutgoing();
    }

    private static void topicPulRequest() throws Exception {
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(tstBrokerURL);
        Connection connection = connectionFactory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Topic topic = session.createTopic(topicName);
        MessageConsumer messageConsumer = session.createConsumer(topic);
        messageConsumer.setMessageListener(new JMSConsumer());
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        logger.info("Press enter to quit application");

        stdin.readLine();
        connection.close();
    }

    private static void topicQueueLasOutgoing() throws Exception {
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(tstBrokerURL);
        Connection connection = connectionFactory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue queue = session.createQueue(queueName);
        MessageConsumer messageConsumer = session.createConsumer(queue);
        messageConsumer.setMessageListener(new JMSConsumer());
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        logger.info("Press enter to quit application");

        stdin.readLine();
        connection.close();
    }

    @Override
    public void onMessage(Message message) {
        try {
            TextMessage textMessage = (TextMessage) message;
            logger.info("Message is " + textMessage.getText());
        } catch (JMSException e) {
            logger.error("JMSException", e);
        }
    }

}
