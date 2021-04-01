package org.olamy.test;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class AppTest 
{

    private static final Logger
        HAZELCAST_LOG = LoggerFactory.getLogger( "org.olamy.test.HazelcastLogs");

    private static final Logger LOGGER = LoggerFactory.getLogger(AppTest.class);

    @Test
    public void startDockerFirst() throws Exception
    {
        String hostAddress = InetAddress.getLocalHost().getHostAddress(); //.getLoopbackAddress().getHostAddress();
        hostAddress = "127.0.0.1";
        LOGGER.info("hostAddress: {}", hostAddress);
        Map<String, String> env = new HashMap<>();
        env.put("LOGGING_LEVEL", "DEBUG");
        env.put("JAVA_OPTS", "-Dhazelcast.local.publicAddress=127.0.0.1:5701 -Dhazelcast.config=/opt/hazelcast/config_ext/hazelcast.xml");
        //env.put("JAVA_OPTS", "-Dhazelcast.local.publicAddress=" + hostAddress);// + " -Dhazelcast.config=/opt/hazelcast/config_ext/hazelcast.xml");

        String imageName =  "hazelcast/hazelcast:" + System.getProperty( "hazelcast.version", "3.12.12");
        try (GenericContainer hazelcast =
                 new FixedHostPortGenericContainer(imageName) // FixedHostPortGenericContainer
                     .withFixedExposedPort(5701, 5701)
                     .withFixedExposedPort(55125, 55125)
                     .withFixedExposedPort(55126, 55126)
//                     .withExposedPorts(5701, 55125, 55126)
                     .withEnv(env)
                     .waitingFor(Wait.forLogMessage(".*is STARTED.*", 1))
//                     .withNetworkMode("host")
//                      .waitingFor(Wait.forListeningPort())
                     .withClasspathResourceMapping( "hazelcast-server.xml",
                                                    "/opt/hazelcast/config_ext/hazelcast.xml",
                                                    BindMode.READ_ONLY)
                     .withLogConsumer(new Slf4jLogConsumer(HAZELCAST_LOG)))
        {
            hazelcast.start();

            String host = hazelcast.getContainerIpAddress();
            int port = hazelcast.getMappedPort(5701);

            String member = host+":"+port;
            LOGGER.info("initial hazelcast member {}", member);
            Config config = new Config();
            config.getProperties().put("hazelcast.logging.type", "slf4j");
            JoinConfig joinConfig = new JoinConfig();
            config.getNetworkConfig().setJoin(joinConfig);
            config.getNetworkConfig().setPort(5702);
            config.setInstanceName("foo-bar");

            joinConfig.getMulticastConfig().setEnabled(false);
            TcpIpConfig tcpIpConfig = joinConfig.getTcpIpConfig();
            tcpIpConfig.setEnabled(true);
            tcpIpConfig.setMembers(Arrays.asList(member));

            HazelcastInstance instance = Hazelcast.getOrCreateHazelcastInstance(config);
            Map map = instance.getMap( "foo");

        }
    }
}
