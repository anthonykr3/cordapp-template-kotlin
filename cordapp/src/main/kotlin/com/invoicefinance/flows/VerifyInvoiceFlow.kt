package com.invoicefinance.flows

import co.paralleluniverse.fibers.Suspendable
import com.invoicefinance.contracts.InvoiceContract
import com.invoicefinance.states.InvoiceState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class VerifyInvoiceFlow(val reference: String) : FlowLogic<SignedTransaction>() {
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
        logger.info("Started verifying")
        progressTracker.currentStep = STARTING_TRANSACTION
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val inputState = serviceHub.vaultService.queryBy(InvoiceState::class.java).states.first { it.state.data.issuance.reference.bytes.contentEquals(reference.toByteArray()) }

        logger.info(inputState.toString())
        val outputStateAndContract = StateAndContract(inputState.state.data.copy(verifiedForPayment = true), InvoiceState.INVOICE_CONTRACT_PROGRAM_ID)
        val cmd = Command(InvoiceContract.Commands.Verify(), serviceHub.myInfo.legalIdentities.single().owningKey)

        val builder = TransactionBuilder(notary).withItems(inputState, outputStateAndContract, cmd)

        builder.verify(serviceHub)

        val signedTx = serviceHub.signInitialTransaction(builder)

        val partiesToSign = setOf(
                serviceHub.identityService.requireWellKnownPartyFromAnonymous(inputState.state.data.issuance.party)
        )
        val otherFlows = partiesToSign.map { initiateFlow(it) }

        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, otherFlows, GATHERING_SIGS.childProgressTracker()))
        return subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))
    }
}