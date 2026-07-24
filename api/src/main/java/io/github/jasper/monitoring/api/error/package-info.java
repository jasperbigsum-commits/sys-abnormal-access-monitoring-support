/**
 * Framework-neutral monitoring failure contract.
 *
 * <p>Error codes are stable machine-readable values. Host applications own HTTP, RPC, or message
 * mappings and must not parse exception messages. Messages and causes must never expose credentials,
 * raw payloads, SQL, or other sensitive data to external clients.</p>
 */
package io.github.jasper.monitoring.api.error;
