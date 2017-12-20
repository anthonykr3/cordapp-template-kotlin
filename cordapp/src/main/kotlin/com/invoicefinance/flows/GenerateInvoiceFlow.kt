package com.invoicefinance.flows

import co.paralleluniverse.fibers.Suspendable
import com.invoicefinance.InvoiceContract
import com.invoicefinance.InvoiceState
import net.corda.core.contracts.Command
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.CASH
import java.time.Instant

@InitiatingFlow
@StartableByRPC
class GenerateInvoiceFlow(val reference: String, val invoiceAmount: Int, val dueOn: Instant, val debtorIdentity: String) : FlowLogic<SignedTransaction>() {
    companion object {
        object STARTING_TRANSACTION : ProgressTracker.Step("Starting")
        object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                STARTING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = STARTING_TRANSACTION
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        val debtorName = CordaX500Name.parse(debtorIdentity)
        val debtor = serviceHub.networkMapCache.getPeerByLegalName(debtorName) ?: throw IllegalArgumentException("Invalid name")

        val outputState = InvoiceState(PartyAndReference(ourIdentity, OpaqueBytes(reference.toByteArray())), ourIdentity, debtor, invoiceAmount.DOLLARS.CASH.amount, dueOn, false, UniqueIdentifier())
        val outputStateAndContract = StateAndContract(outputState, InvoiceState.INVOICE_CONTRACT_PROGRAM_ID)
        val cmd = Command(InvoiceContract.Commands.Create(), listOf(ourIdentity.owningKey, debtor.owningKey))

        val builder = TransactionBuilder(notary).withItems(outputStateAndContract, cmd)
        builder.setTimeWindow(serviceHub.clock.instant(), 30.seconds)
        builder.verify(serviceHub)

        val signedTx = serviceHub.signInitialTransaction(builder)
        val otherPartySession = initiateFlow(debtor)
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(otherPartySession), GATHERING_SIGS.childProgressTracker()))
        return subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))
    }
}

