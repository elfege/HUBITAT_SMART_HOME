def postRebootCommandToRemoteHub() {
    def serverURI = state.remoteUri.split('/')[0..2].join('/')
    def remoteMAC = "34:e1:d1:81:b3:1e"
    def formattedMac = remoteMAC.toLowerCase().replaceAll(":", "")
    def tokens = [state.remoteManagementToken, formattedMac, remoteMAC, state.remoteToken]

    log.debug tokens

    def attemptRequest = { uri, token, authType ->
        def status = null
        def requestParams = [
            uri: uri,
            contentType: "application/json"
        ]

        if (authType == "Bearer") {
            requestParams.headers = ["Authorization": "Bearer ${token}"]
        } else if (authType == "Basic") {
            def encodedToken = token.bytes.encodeBase64().toString()
            requestParams.headers = ["Authorization": "Basic ${encodedToken}"]
        } else if (authType == "Digest") {
            // For Digest Auth, a specific handling is needed, often involving more steps
            requestParams.headers = ["Authorization": "Digest ${token}"]  // Example, may need proper implementation
        } else if (authType == "MACColon") {
            // Custom handling where MAC with colons is used directly as a password or token
            requestParams.headers = ["Authorization": "${remoteMAC}"]
        } else if (authType == "FormattedMAC") {
            // Custom handling where formatted MAC (without colons) is used
            requestParams.headers = ["Authorization": "${formattedMac}"]
        } else if (authType == "BasicWithMAC") {
            def encodedToken = ":${remoteMAC}".bytes.encodeBase64().toString()
            requestParams.headers = ["Authorization": "Basic ${encodedToken}"]
        }

        log.debug "Attempting POST request to ${uri} with token ${token} and authType ${authType}"
        httpPost(requestParams) { response ->
            log.debug "POST response: ${response.status}"
            log.debug "Response body: ${response.data}"
            status = response.status // Capture the status
        }
        log.warn "attemptRequest returns status: $status"
        return status
    }
    
    def authTypes = ["Bearer", "Basic", "Digest", "MACColon", "FormattedMAC", "BasicWithMAC"]

    // Try the 8081 port with /api/rebootHub
    try {
        def fullUri = "${serverURI}:8081/api/rebootHub"
        for (authType in authTypes) {
            for (token in tokens) {
                try {
                    def status = attemptRequest(fullUri, token, authType)
                    log.warn "status = $status"
                    if (status == 200) {
                        log.trace formatText("Attempt on 8081 with token ${token} and authType ${authType} SUCCEEDED - remote hub is now rebooting", "black", "green")
                        return  // Success, exit the function
                    }
                } catch (Exception e) {
                    log.warn "Attempt on 8081 failed with token ${token} and authType ${authType}: ${e.message}"
                }
            }
        }
    } catch (Exception e) {
        log.error "All attempts on port 8081 failed: ${e.message}"
    }

    // // Try the 8080 port with /hub/reboot
    try {
        def fullUri = "${serverURI}:8080/hub/reboot"
        for (token in tokens) {
            try {
                def status = attemptRequest(fullUri, token, "Bearer")
                log.warn "status = $status"
                if (status == 200) {
                    log.trace formatText("Attempt on 8080 with token ${token} SUCCEEDED - remote hub is now rebooting", "black", "orange")
                    return  // Success, exit the function
                }
            } catch (Exception e) {
                log.warn "Attempt on 8080 failed with token ${token}: ${e.message}"
            }
        }
    } catch (Exception e) {
        log.error "All attempts on port 8080 failed: ${e.message}"
    }

    // If all attempts fail, throw the final exception
    throw new Exception("All POST attempts to reboot the remote hub have failed.")
}