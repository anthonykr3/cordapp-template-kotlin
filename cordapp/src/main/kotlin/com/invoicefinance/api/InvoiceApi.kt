package com.invoicefinance.api

import com.invoicefinance.flows.GenerateInvoiceFlow
import com.invoicefinance.flows.VerifyInvoiceFlow
import com.invoicefinance.states.InvoiceState
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import java.time.Instant
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

val SERVICE_NAMES = listOf("Controller", "Network Map Service")


data class InvoiceModel(
        val reference: String,
        val issuer: AbstractParty,
        val owner: AbstractParty,
        val debtor: AbstractParty,
        val invoiceAmount: Amount<Currency>,
        val dueOn: Instant,
        val verifiedForPayment: Boolean
) {
    constructor(invoice: InvoiceState) : this(
            String(invoice.issuance.reference.bytes),
            invoice.issuance.party,
            invoice.owner,
            invoice.debtor,
            invoice.invoiceAmount,
            invoice.dueOn,
            invoice.verifiedForPayment)
}

@Path("invoicefinance")
class InvoiceApi(val rpcOps: CordaRPCOps) {
    private val myLegalName: CordaX500Name = rpcOps.nodeInfo().legalIdentities.first().name

    companion object {
        private val logger: Logger = loggerFor<InvoiceApi>()
    }

    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = rpcOps.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    /**
     * Displays all invoice states that exist within the vault
     */
    @GET
    @Path("invoices")
    @Produces(MediaType.APPLICATION_JSON)
    fun getIOUs() = rpcOps.vaultQueryBy<InvoiceState>().states.map { InvoiceModel(it.state.data) }

    /**
     * Initiates a flow to agree an IOU between two parties.
     *
     * Once the flow finishes it will have written the IOU to ledger. Both the lender and the borrower will be able to
     * see it when calling /api/example/ious on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PUT
    @Path("invoices")
    fun createInvoice(@QueryParam("reference") reference: String, @QueryParam("invoiceAmount") invoiceAmount: Int, @QueryParam("dueOn") dueOn: String, @QueryParam("debtorIdentity") debtorIdentity: CordaX500Name?): Response {
        if (invoiceAmount <= 0 ) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Query parameter 'invoiceAmount' must be non-negative.\n").build()
        }
        if (debtorIdentity == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Query parameter 'partyName' missing or has wrong format.\n").build()
        }

        return try {
            val flowHandle = rpcOps.startTrackedFlow(::GenerateInvoiceFlow,
                    reference,
                    invoiceAmount,
                    Instant.parse(dueOn),
                    debtorIdentity)
            flowHandle.progress.subscribe { println(">> $it") }

            // The line below blocks and waits for the future to resolve.
            val result = flowHandle.returnValue.getOrThrow()

            Response.status(Response.Status.CREATED).entity("Transaction id ${result.id} committed to ledger.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    /**
     * Initiates a flow to agree an IOU between two parties.
     *
     * Once the flow finishes it will have written the IOU to ledger. Both the lender and the borrower will be able to
     * see it when calling /api/example/ious on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PUT
    @Path("verifyinvoice")
    fun verifyInvoice(@QueryParam("reference") reference: String): Response {
        return try {
            val flowHandle = rpcOps.startTrackedFlow(::VerifyInvoiceFlow, reference)
            flowHandle.progress.subscribe { println(">> $it") }

            // The line below blocks and waits for the future to resolve.
            val result = flowHandle.returnValue.getOrThrow()

            Response.status(Response.Status.CREATED).entity("Transaction id ${result.id} committed to ledger.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.BAD_REQUEST).entity(ex.message!!).build()
        }
    }
}