package io.testkit.adapters.dubbo;

import io.testkit.basetest.identity.IdentityContext;
import java.time.Duration;
import java.util.Map;
import org.apache.dubbo.config.*;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.service.GenericService;

/** Explicitly owned reference; one service/interface/version/group per client. */
public final class DubboClient implements AutoCloseable {
  private final ReferenceConfig<GenericService> reference;
  private GenericService service;
  private boolean closed;
  private final String interfaceName;
  private final String target;

  public DubboClient(
      String application,
      String interfaceName,
      String group,
      String version,
      String registry,
      String directUrl,
      Duration timeout) {
    if (timeout.isNegative() || timeout.toMillis() <= 0 || timeout.toMillis() > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Dubbo timeout must fit positive milliseconds");
    if ((registry == null || registry.isBlank()) && (directUrl == null || directUrl.isBlank()))
      throw new IllegalArgumentException("Registry or direct URL required");
    reference = new ReferenceConfig<>();
    this.interfaceName = interfaceName;
    this.target = directUrl == null || directUrl.isBlank() ? "registry-service" : directUrl;
    ApplicationConfig app = new ApplicationConfig(application);
    app.setQosEnable(false);
    reference.setApplication(app);
    reference.setInterface(interfaceName);
    reference.setGeneric("true");
    reference.setGroup(group);
    reference.setVersion(version);
    reference.setRetries(0);
    reference.setTimeout((int) timeout.toMillis());
    reference.setCheck(true);
    reference.setRegistry(
        new RegistryConfig(registry == null || registry.isBlank() ? "N/A" : registry));
    if (directUrl != null && !directUrl.isBlank()) reference.setUrl(directUrl);
  }

  public synchronized Object invoke(
      String method, String[] parameterTypes, Object[] arguments, Map<String, String> attachments) {
    if (closed) throw new IllegalStateException("Dubbo client closed");
    if (parameterTypes.length != arguments.length)
      throw new IllegalArgumentException("Dubbo argument/type count mismatch");
    var context = RpcContext.getClientAttachment();
    var previous = new java.util.HashMap<>(context.getObjectAttachments());
    java.time.Instant started = java.time.Instant.now();
    long nanos = System.nanoTime();
    Throwable primary = null;
    Object result = null;
    var requestLog = new java.util.LinkedHashMap<String, Object>();
    requestLog.put("interface", interfaceName);
    requestLog.put("method", method);
    requestLog.put("parameterTypes", parameterTypes);
    requestLog.put("arguments", arguments);
    requestLog.put("attachments", attachments);
    var callLog = io.testkit.basetest.runtime.ProtocolCallLog.start("DUBBO", target, requestLog);
    try {
      if (service == null) service = reference.get();
      attachments.forEach(context::setAttachment);
      result = service.$invoke(method, parameterTypes, arguments);
      return result;
    } catch (RuntimeException | Error failure) {
      primary = failure;
      throw failure;
    } finally {
      var responseLog = new java.util.LinkedHashMap<String, Object>();
      responseLog.put("body", result);
      callLog.complete(responseLog, primary);
      context.clearAttachments();
      previous.forEach(context::setObjectAttachment);
      try {
        io.testkit.basetest.runtime.RuntimeRegistry.current()
            .invocationLogs()
            .accept(
                new io.testkit.basetest.runtime.InvocationRecord(
                    started,
                    "dubbo",
                    "generic-call",
                    Map.of(),
                    null,
                    (System.nanoTime() - nanos) / 1_000_000,
                    primary == null ? null : primary.getClass().getSimpleName()));
      } catch (RuntimeException logging) {
        if (primary != null) primary.addSuppressed(logging);
        else throw logging;
      }
    }
  }

  public Object authenticated(
      String method, String[] types, Object[] args, String tokenAttachment) {
    return invoke(
        method,
        types,
        args,
        Map.of(tokenAttachment, IdentityContext.requireCurrent().accessToken()));
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    reference.destroy();
  }
}
