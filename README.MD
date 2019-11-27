##Description
Test implemented by using kotlin coroutines and ktor.

###Authentication
For endpoints that need authentication, just pass tenantId as query parameter 

###Endpoints: 
`/flats/{id}` - returns information about flat
`/flats/{id}/schedules` - returns information about schedules
`/flats/{id}/schedules/{time}/reserve` - allows to make a reservation for specified time (**need authorization**)
`/flats/{id}/schedules/{time}/cancel` - allows to cancel reservation for previosly called `reserve` (**need authorization**)
`/flats/{id}/schedules/{time}/confirm` - allows to confirm reservation for current tenant (**need authorization**)
`/flats/{id}/schedules/{time}/reject` - allows to reject reservation for current tenant (**need authorization**)
`/notifications/{from?}` - returns notifications (**need authorization**)

###Running tests
gradle test

###Running server
gradle run