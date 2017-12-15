package com.invoicefinance

import net.corda.core.utilities.days
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.testing.*
import org.junit.Test

class InvoiceContractTests {
    private fun getInvoice() = InvoiceState(
            issuance = MINI_CORP.ref(123),
            owner = MINI_CORP,
            debtor = MEGA_CORP,
            invoiceAmount = 1234.DOLLARS `issued by` MINI_CORP.ref(123),
            dueOn = TEST_TX_TIME + 30.days,
            verifiedForPayment = false
    )

    @Test
    fun `Test that an invoice is created successfully`() {
        val output = getInvoice()
        ledger {
            transaction {
                attachments(InvoiceState.INVOICE_CONTRACT_PROGRAM_ID)
                timeWindow(TEST_TX_TIME)
                tweak {
                    output(InvoiceState.INVOICE_CONTRACT_PROGRAM_ID, "invoice", output)
                    command(MEGA_CORP_PUBKEY) { InvoiceContract.Commands.Create() }
                    `fails with`("There must be two signatories")
                }
                tweak {
                    var newOutput = output.copy(owner = BIG_CORP)
                    output(InvoiceState.INVOICE_CONTRACT_PROGRAM_ID, "invoice", newOutput)
                    command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { InvoiceContract.Commands.Create() }
                    `fails with`("The owner is the issuer on issuance")
                }
                command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { InvoiceContract.Commands.Create() }
                tweak {
                    var newOutput = output.copy(debtor = output.issuance.party)
                    output(InvoiceState.INVOICE_CONTRACT_PROGRAM_ID, "invoice", newOutput)
                    `fails with`("The issuer and the debtor cannot be the same entity")
                }
                tweak {
                    var newOutput = output.copy(owner = output.debtor)
                    output(InvoiceState.INVOICE_CONTRACT_PROGRAM_ID, "invoice", newOutput)
                    `fails with`("The owner and the debtor cannot be the same entity")
                }
                tweak {
                    var newOutput = output.copy(invoiceAmount = 0.DOLLARS `issued by` MINI_CORP.ref(123))
                    output(InvoiceState.INVOICE_CONTRACT_PROGRAM_ID, "invoice", newOutput)
                    `fails with`("The invoice amount should be greater than zero")
                }
                tweak {
                    var newOutput = output.copy(verifiedForPayment = true)
                    output(InvoiceState.INVOICE_CONTRACT_PROGRAM_ID, "invoice", newOutput)
                    `fails with`("The invoice creator can't verify it")
                }
                output(InvoiceState.INVOICE_CONTRACT_PROGRAM_ID, "invoice", output)
                verifies()
            }
        }
    }
}