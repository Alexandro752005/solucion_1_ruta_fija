package pe.rutafija.support;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.*;
/** Single public transport probe, no DB, accounts, location, credentials or SMTP. */
public final class L08PhysicalNetworkProbe {
 public static void main(String[] args)throws Exception{
  if(args.length!=0||!"ONE_PHONE_PUBLIC_TCP_PROBE".equals(System.getenv("RF_L08_NETWORK_PROBE_MODE")))throw new IllegalStateException("EXACT_PROBE_MODE_REQUIRED");
  String bind=System.getenv("RF_L08_FIS02_BIND_IPV4"),phone=System.getenv("RF_L08_FIS02_PHONE_IPV4");
  if(!L08ConnectedTrial.privateIpv4(bind)||!L08ConnectedTrial.privateIpv4(phone))throw new IllegalStateException("EXACT_PRIVATE_PEERS_REQUIRED");
  Path out=Path.of(System.getenv("RF_L08_NETWORK_PROBE_OUTPUT"));if(Files.exists(out))throw new IllegalStateException("KEEP_PRIOR_OBSERVATION");
  var signal=new CountDownLatch(1);var success=new java.util.concurrent.atomic.AtomicBoolean();
  var server=HttpServer.create(new InetSocketAddress(bind,4311),0);
  server.createContext("/l08-fis02-network-probe",e->{try{
   boolean allowed=phone.equals(e.getRemoteAddress().getAddress().getHostAddress())&&"GET".equals(e.getRequestMethod())&&"/l08-fis02-network-probe".equals(e.getRequestURI().toString());
   byte[] body=(allowed?"L08_FIS02_NETWORK_PROBE=PASS":"REFUSED").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
   e.getResponseHeaders().set("Cache-Control","no-store");e.sendResponseHeaders(allowed?200:403,body.length);e.getResponseBody().write(body);if(allowed){success.set(true);signal.countDown();}
  }finally{e.close();}});server.start();
  System.out.println("L08_NETWORK_PROBE=READY_NO_DATABASE_NO_ACCOUNTS");
  try{signal.await(60,TimeUnit.SECONDS);}finally{server.stop(0);}
  String json="{\"runId\":\"3f0ba758-6eb9-4d2f-b948-dd1c7de3c430\",\"result\":\""+(success.get()?"PASS_REAL_PHONE_TCP_ONE_PEER_ONLY":"FAIL_PHONE_TRANSPORT_NOT_OBSERVED")+"\",\"networkManifestSha256\":\""+System.getenv("RF_L08_NETWORK_MANIFEST_SHA256")+"\",\"userReportedElevatedPreparation\":\"READY_ONE_PHONE_PRIVATE_PROFILE_ONLY\",\"observedAt\":\""+Instant.now()+"\",\"databaseConnected\":false,\"accountsCreated\":0,\"mailSent\":0,\"javaServerStopped\":true,\"firewallPolicyIndependentlyRereadByAgent\":false}";
  Files.createDirectories(out.getParent());Files.writeString(out,json,StandardOpenOption.CREATE_NEW);System.out.println("L08_NETWORK_PROBE="+(success.get()?"PASS_REAL_PHONE_TCP_ONE_PEER_ONLY":"FAIL_KEEP_OBSERVATION"));if(!success.get())System.exit(1);
 }
}
