package model

case class Account(
    id: Snowflake,
    label: String,
    balance: DkpAmount,
    useDecay: Boolean
)
