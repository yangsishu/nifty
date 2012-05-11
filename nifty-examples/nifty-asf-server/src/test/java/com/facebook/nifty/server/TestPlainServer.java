package com.facebook.nifty.server;

import com.facebook.nifty.core.NiftyBootstrap;
import com.facebook.nifty.core.ThriftServerDefBuilder;
import com.facebook.nifty.guice.NiftyModule;
import com.facebook.nifty.test.LogEntry;
import com.facebook.nifty.test.ResultCode;
import com.facebook.nifty.test.scribe;
import com.google.inject.Guice;
import com.google.inject.Stage;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;

/**
 * Author @jaxlaw
 * Date: 4/24/12
 * Time: 3:56 PM
 */
public class TestPlainServer {

  private static final Logger log = LoggerFactory.getLogger(TestPlainServer.class);

  public static final String VERSION = "1.0";
  private NiftyBootstrap bootstrap;
  private int port;

  @BeforeTest(alwaysRun = true)
  public void setup() {

    try {
      ServerSocket s = new ServerSocket();
      s.bind(new InetSocketAddress(0));
      port = s.getLocalPort();
      s.close();
    } catch (IOException e) {
      port = 8080;
    }

    bootstrap = Guice.createInjector
      (
        Stage.PRODUCTION,
        new NiftyModule() {
          @Override
          protected void configureNifty() {
            bind().toInstance(
              new ThriftServerDefBuilder()
                .listen(port)
                .withProcessor(
                  new scribe.Processor(
                    new scribe.Iface() {
                      @Override
                      public ResultCode Log(List<LogEntry> messages) throws TException {
                        for (LogEntry message : messages) {
                          log.info("%s: %s", message.getCategory(), message.getMessage());
                        }
                        return ResultCode.OK;
                      }
                    }
                  )
                ).build()
            );
          }
        }
      )
      .getInstance(NiftyBootstrap.class);

    bootstrap.start();

  }

  @Test(groups = "fast")
  public void testMethodCalls() throws Exception {
    scribe.Client client = makeClient();
    client.Log(Arrays.asList(new LogEntry("hello", "world")));
  }

  private scribe.Client makeClient() throws TTransportException {
    TSocket socket = new TSocket("localhost", port);
    socket.open();
    TBinaryProtocol tp = new TBinaryProtocol(new TFramedTransport(socket));
    return new scribe.Client(tp);
  }


  @AfterTest(alwaysRun = true)
  public void teardown() throws InterruptedException {
    if (bootstrap != null) {
      bootstrap.stop();
    }
  }

}