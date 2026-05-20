package com.taxoin.api;

import com.taxoin.api.dto.*;
import com.taxoin.contrib.AttestedTransaction;
import com.taxoin.contrib.ServiceRegistration;
import com.taxoin.core.Account;
import com.taxoin.crypto.CryptoUtils;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.security.KeyPair;
import java.util.List;
import java.util.Map;

/**
 * Taxoin REST API — Quarkus port of api.py.
 *
 * Endpoints:
 *   GET  /health
 *   GET  /q/health/live          (Quarkus built-in)
 *   GET  /api/status
 *   POST /api/wallet
 *   GET  /api/balance/{address}
 *   POST /api/tx/send
 *   GET  /api/services
 *   POST /api/service/register
 *   GET  /api/reputation/{address}
 *   GET  /api/validators
 *   POST /api/testnet/faucet
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaxoinApi {

    @Inject
    ApplicationState state;

    // ── Health ────────────────────────────────────────────────────────────────

    @GET
    @Path("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @GET
    @Path("/api/status")
    public StatusResponse getStatus() {
        StatusResponse resp = new StatusResponse();
        try {
            resp.validators = state.getManager().listBranches().size();
        } catch (Exception ignored) {}
        try {
            resp.services = state.getServiceRegistry().listServices().size();
        } catch (Exception ignored) {}
        return resp;
    }

    // ── Wallet ────────────────────────────────────────────────────────────────

    @POST
    @Path("/api/wallet")
    public WalletResponse createWallet() {
        KeyPair kp = CryptoUtils.generateKeypair();
        return new WalletResponse(
                CryptoUtils.publicKeyToAddress(kp.getPublic()),
                CryptoUtils.privateKeyToPem(kp.getPrivate()),
                CryptoUtils.publicKeyToPem(kp.getPublic()));
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    @GET
    @Path("/api/balance/{address}")
    public BalanceResponse getBalance(@PathParam("address") String address) {
        return new BalanceResponse(address, state.getBalance(address));
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    @POST
    @Path("/api/tx/send")
    public TxResponse sendTx(TxSendRequest req) {
        String ref = (req.serviceRef != null && !req.serviceRef.isEmpty())
                ? req.serviceRef : "svc:" + req.provider;
        AttestedTransaction tx = new AttestedTransaction(
                req.consumer, req.provider, ref, req.amount,
                req.consumerSig, req.providerSig);
        return new TxResponse(
                tx.txId,
                tx.isValid() ? "pending" : "invalid",
                tx.amount, tx.consumer, tx.provider);
    }

    // ── Services ──────────────────────────────────────────────────────────────

    @GET
    @Path("/api/services")
    public List<ServiceResponse> listServices(
            @QueryParam("service_type") String serviceType,
            @QueryParam("min_rating")   @DefaultValue("0.0") double minRating) {
        return state.getServiceRegistry()
                .listServices(serviceType, minRating)
                .stream()
                .map(s -> new ServiceResponse(
                        s.provider, s.serviceType, s.pricePerUnit, s.description,
                        state.getReputation().getRating(s.provider),
                        state.getReputation().getSuccessfulTxCount(s.provider)))
                .toList();
    }

    @POST
    @Path("/api/service/register")
    public Response registerService(ServiceRegisterRequest req) {
        ServiceRegistration svc = new ServiceRegistration(
                req.provider, req.serviceType, req.pricePerUnit,
                req.description, req.endpoint);
        boolean ok = state.getServiceRegistry().register(svc);
        if (!ok) {
            return Response.status(400)
                    .entity(Map.of("error", "Service already registered for this provider"))
                    .build();
        }
        return Response.ok(Map.of("status", "ok", "provider", req.provider)).build();
    }

    // ── Reputation ────────────────────────────────────────────────────────────

    @GET
    @Path("/api/reputation/{address}")
    public Map<String, Object> getReputation(@PathParam("address") String address) {
        var rep = state.getReputation();
        return Map.of(
                "address",        address,
                "rating",         rep.getRating(address),
                "successful_tx",  rep.getSuccessfulTxCount(address),
                "disputes",       rep.getDisputeCount(address));
    }

    // ── Validators ────────────────────────────────────────────────────────────

    @GET
    @Path("/api/validators")
    public List<String> getValidators() {
        try {
            return state.getManager().listBranches();
        } catch (Exception e) {
            return List.of("main");
        }
    }

    // ── Testnet faucet ────────────────────────────────────────────────────────

    @POST
    @Path("/api/testnet/faucet")
    public Response faucet(Map<String, String> data) {
        String address = data == null ? "" : data.getOrDefault("address", "");
        if (address.isEmpty()) {
            return Response.status(400)
                    .entity(Map.of("error", "Missing address"))
                    .build();
        }
        try {
            var main = state.getManager().getMainState();
            if (main != null) {
                Account acct = main.getAccount(address);
                acct.balance += 100.0;
            }
        } catch (Exception e) {
            state.getBalances().merge(address, 100.0, Double::sum);
        }
        return Response.ok(Map.of(
                "status", "ok",
                "address", address,
                "amount", 100.0)).build();
    }
}
