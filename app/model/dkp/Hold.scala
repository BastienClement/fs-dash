package model.dkp

import model.Snowflake

case class Hold(
    id: Snowflake,
    account: Snowflake,
    amount: DkpAmount,
    label: String
)
