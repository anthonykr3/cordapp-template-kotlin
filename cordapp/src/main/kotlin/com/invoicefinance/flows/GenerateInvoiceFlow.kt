package com.invoicefinance.flows

import co.paralleluniverse.fibers.Suspendable
import com.invoicefinance.InvoiceContract
import com.invoicefinance.InvoiceState
import net.corda.core.contracts.Command
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.CASH
import java.time.Instant

@InitiatingFlow
@StartableByRPC
class GenerateInvoiceFlow(val reference: String, val invoiceAmount: Int, val dueOn: Instant, val debtor: Party) : FlowLogic<Unit>() {
    override val progressTracker: ProgressTracker = ProgressTracker()

    @Suspendable
    override fun call() {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        val outputState = InvoiceState(PartyAndReference(ourIdentity, OpaqueBytes(reference.toByteArray())), ourIdentity, debtor, invoiceAmount.DOLLARS.CASH.amount, dueOn, false)
        val outputStateAndContract = StateAndContract(outputState, InvoiceState.INVOICE_CONTRACT_PROGRAM_ID)
        var cmd = Command(InvoiceContract.Commands.Create(), listOf(ourIdentity.owningKey, debtor.owningKey))

        var builder = TransactionBuilder(notary).withItems(outputStateAndContract, cmd)
        builder.setTimeWindow(serviceHub.clock.instant(), 30.seconds)
        builder.verify(serviceHub)

        var signedTx = serviceHub.signInitialTransaction(builder)
        var otherPartySession = initiateFlow(debtor)
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(otherPartySession), CollectSignaturesFlow.tracker()))
        subFlow(FinalityFlow(fullySignedTx))
    }
}

@InitiatingFlow
@StartableByRPC
class VerifyInvoiceFlow(val reference: String, val invoiceAmount: Int) : FlowLogic<Unit>() {
    override val progressTracker: ProgressTracker = ProgressTracker()

    @Suspendable
    override fun call () {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]




    }
}
