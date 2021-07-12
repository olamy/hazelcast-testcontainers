package org.olamy.test;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;


public class AppTest 
{

    private static final Logger
        HAZELCAST_LOG = LoggerFactory.getLogger( "org.olamy.test.HazelcastDockerLogs");

    private static final Logger LOGGER = LoggerFactory.getLogger(AppTest.class);

    @Test
    public void startDockerFirst() throws Exception
    {
        //Testcontainers.exposeHostPorts(5705, 5701);

        Map<String, String> env = new HashMap<>();
        InetAddress inetAddress = InetAddress.getLocalHost();
        String localIp = inetAddress.getHostAddress();
        LOGGER.info("localIp: {}", localIp);
        //Network network = Network.newNetwork();
        // -Dhazelcast.local.publicAddress=localhost
        String extOpts = "-Dhazelcast.local.publicAddress="+localIp; // 127.0.0.1;// "-Dhazelcast.local.publicAddress=host.testcontainers.internal";
        env.put("JAVA_OPTS", extOpts + " -Dhazelcast.config=/opt/hazelcast/config_ext/hazelcast.xml -Dhazelcast.diagnostics.enabled=true");
        env.put("HZ_NETWORK_PUBLICADDRESS", localIp+":5701"); // 127.0.0.1
        String imageName =  "hazelcast/hazelcast:" + System.getProperty( "hazelcast.version", "3.12.12");
        try (GenericContainer hazelcast =
                 new FixedHostPortGenericContainer(imageName) //  GenericContainer
                     .withFixedExposedPort(5701, 5701)
                     //.withFixedExposedPort(55125, 55125)
                     //.withFixedExposedPort( 55126, 55126)
                     //.withExposedPorts(55125, 55126)
                     .withEnv(env)
                     .waitingFor(Wait.forLogMessage(".*is STARTED.*", 1))
                     .withClasspathResourceMapping( "hazelcast-server.xml",
                                                    "/opt/hazelcast/config_ext/hazelcast.xml",
                                                    BindMode.READ_ONLY)
                     .withLogConsumer(new Slf4jLogConsumer(HAZELCAST_LOG)))
        {
            //hazelcast.setPortBindings(Arrays.asList("5701:5701", "55125:55125", "55126:55126"));
            hazelcast.start();

            String host = InetAddress.getByName(hazelcast.getContainerIpAddress()).getHostAddress();
            LOGGER.info("hazelcast.getContainerIpAddress():{}", hazelcast.getContainerIpAddress());
            int port = 5701; // hazelcast.getMappedPort(5701);

            String member = host+":"+port;
            LOGGER.info("initial hazelcast member {}", member);

            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<hazelcast xsi:schemaLocation=\"http://www.hazelcast.com/schema/config hazelcast-config-3.8.xsd\"\n"
                + "           xmlns=\"http://www.hazelcast.com/schema/config\"\n"
                + "           xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "<instance-name>foobar</instance-name>\n"
                + "  <network>\n"
                + "    <port port-count=\"20\">5705</port>\n"
                + "    <join>\n"
                + "      <multicast enabled=\"false\">\n"
                + "      </multicast>\n"
                + "      <tcp-ip enabled=\"true\">\n"
                + "        <member-list>\n"
                + "          <member>"+member+"</member>\n"
                + "        </member-list>\n" + "      </tcp-ip>\n"
                + "      <aws enabled=\"false\"/>\n"
                + "    </join>\n"
                + "  </network>\n"
                + "  <properties>\n"
                + "    <property name=\"hazelcast.logging.type\">true</property>\n"
                + "  </properties>"
                + "</hazelcast>";

            LOGGER.info("xml {}", xml);

            Config config = new XmlConfigBuilder(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).build();

            HazelcastInstance instance = Hazelcast.getOrCreateHazelcastInstance(config);

            for (int i = 0; i < 10000; i++) {
                instance.getMap("FOO").put("test", i);
            }
            assertEquals(2, instance.getCluster().getMembers().size());
            instance.getCluster().getMembers().forEach(clusterMember ->
                {
                    LOGGER.info("clusterMember/address: {}/{}", clusterMember, clusterMember.getAddress());

                }
            );




        }
    }


    //@Test
    public void startDockerSecond() throws Exception
    {

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<hazelcast xsi:schemaLocation=\"http://www.hazelcast.com/schema/config hazelcast-config-3.8.xsd\"\n"
            + "           xmlns=\"http://www.hazelcast.com/schema/config\"\n"
            + "           xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
            + "<instance-name>foobar</instance-name>\n"
            + "  <network>\n"
            + "    <port port-count=\"20\">5701</port>\n"
            + "    <join>\n"
            + "      <multicast enabled=\"true\">\n"
            + "      </multicast>\n"
            + "      <tcp-ip enabled=\"false\">\n"
            //+ "        <member-list>\n"
            //+ "          <member>"+member+"</member>\n"
            //+ "        </member-list>\n"
            + "      </tcp-ip>\n"
            + "      <aws enabled=\"false\"/>\n"
            + "    </join>\n"
            + "  </network>\n"
            + "  <properties>\n"
            + "    <property name=\"hazelcast.logging.type\">true</property>\n"
            + "  </properties>"
            + "</hazelcast>";

        LOGGER.info("xml {}", xml);

        Config config = new XmlConfigBuilder(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).build();

        HazelcastInstance instance = Hazelcast.getOrCreateHazelcastInstance(config);


        Map<String, String> env = new HashMap<>();
        InetAddress inetAddress = InetAddress.getLocalHost();
        String localIp = inetAddress.getHostAddress();
        LOGGER.info("localIp: {}", localIp);
        //Network network = Network.newNetwork();
        // -Dhazelcast.local.publicAddress=localhost
        String extOpts = "-Dhazelcast.local.publicAddress="+localIp; // 127.0.0.1;// "-Dhazelcast.local.publicAddress=host.testcontainers.internal";
        env.put("JAVA_OPTS", extOpts + " -Dhazelcast.config=/opt/hazelcast/config_ext/hazelcast.xml -Dhazelcast.diagnostics.enabled=true");
        env.put("HZ_NETWORK_PUBLICADDRESS", localIp+":5701"); // 127.0.0.1
        String imageName =  "hazelcast/hazelcast:" + System.getProperty( "hazelcast.version", "3.12.12");
        try (GenericContainer hazelcast =
                 new FixedHostPortGenericContainer(imageName) //  GenericContainer
                     .withFixedExposedPort(5702, 5701)
                     //.withFixedExposedPort(55125, 55125)
                     //.withFixedExposedPort( 55126, 55126)
                     //.withExposedPorts(55125, 55126)
                     .withEnv(env)
                     .waitingFor(Wait.forLogMessage(".*is STARTED.*", 1))
                     .withClasspathResourceMapping( "hazelcast-server-second.xml",
                                                    "/opt/hazelcast/config_ext/hazelcast.xml",
                                                    BindMode.READ_ONLY)
                     .withLogConsumer(new Slf4jLogConsumer(HAZELCAST_LOG)))
        {
            //hazelcast.setPortBindings(Arrays.asList("5701:5701", "55125:55125", "55126:55126"));
            hazelcast.start();

            String host = InetAddress.getByName(hazelcast.getContainerIpAddress()).getHostAddress();
            LOGGER.info("hazelcast.getContainerIpAddress():{}", hazelcast.getContainerIpAddress());
            int port = 5701; // hazelcast.getMappedPort(5701);

            String member = host+":"+port;
            LOGGER.info("initial hazelcast member {}", member);


            Map map = instance.getMap("foo");

            assertEquals(2, instance.getCluster().getMembers().size());
            instance.getCluster().getMembers().forEach(clusterMember -> LOGGER.info("clusterMember: {}", clusterMember));

        }
    }

}
