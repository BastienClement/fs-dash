package model

case class Transaction(
    id: Snowflake,
    label: String,
    details: String
)
