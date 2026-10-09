# Simple Home deployment

Deploy Simple Home through the installed Simple Home Dev app, using `scripts/deploy-dev.ps1` and the ignored `.hubitat-dev.json` endpoint configuration.

Do not deploy individual Simple Home Apps Code, Drivers Code, or libraries directly through MCP. MCP is appropriate for read-only inspection and verification. Keep fixes in this repository, run `check packageHubitat verifyHubitatPackage`, commit and push to `main`, then use the Dev deployment workflow.

After deployment, verify the live source and the Dev app post-update status. If the endpoint fails, inspect the hub before retrying; a failed HTTP response may follow a partial update. Do not expose endpoint access tokens or hub credentials.
