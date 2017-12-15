package com.invoicefinance

import net.corda.core.contracts.*
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant
import java.util.*

data class InvoiceState(
        val issuance: PartyAndReference,
        override val owner: AbstractParty,
        var debtor: AbstractParty,
        var invoiceAmount: Amount<Issued<Currency>>,
        val dueOn: Instant,
        val verifiedForPayment: Boolean) : OwnableState, QueryableState {
    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(InvoiceSchemaV1)

    companion object {
        const val INVOICE_CONTRACT_PROGRAM_ID: ContractClassName = "com.invoicefinance.InvoiceContract"
    }

    override fun withNewOwner(newOwner: AbstractParty): CommandAndState = CommandAndState(InvoiceContract.Commands.Move(), this.copy(owner = newOwner))

    override val participants get() = listOf(owner, debtor)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is InvoiceSchemaV1 -> InvoiceSchemaV1.PersistentInvoiceState(
                    issuancePartyHash = this.issuance.party.owningKey.toStringShort(),
                    issuanceRef = this.issuance.reference.bytes,
                    ownerHash = this.owner.owningKey.toStringShort(),
                    debtorHash = this.owner.owningKey.toStringShort(),
                    dueOn = this.dueOn,
                    invoiceAmount = this.invoiceAmount.quantity,
                    invoiceAmountCurrency = this.invoiceAmount.token.product.currencyCode,
                    verifiedForPayment = this.verifiedForPayment
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }
}

