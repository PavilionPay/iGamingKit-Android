[![](https://jitpack.io/v/PavilionPay/iGamingKit-Android.svg)](https://jitpack.io/#PavilionPay/iGamingKit-Android)

# iGamingKit for Android

This repository contains a sample application that demonstrates integration and use of the iGaming SDK for iOS.

Detailed instructions on how to integrate with the iGaming SDK for iOS can be found in our main documentation.

API Reference can be found [here](https://pavilionpay.github.io/iGamingKit-Android/documentation/igamingkit/)

## Getting Started

Follow these steps to integrate the iGaming SDK into your iOS project:

Follow the steps outlined in the [Operator Setup](https://ausenapccde03.azureedge.net/operator-onboarding/operator-setup)


## Installation

Add the following to your app's dependencies in the build.gradle.kts file:

```
  implementation("com.github.PavilionPay:iGamingKit-Android:0.0.6")
```
Add the JitPack repository to your build file
```
	dependencyResolutionManagement {
		repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
		repositories {
			mavenCentral()
			maven { url 'https://jitpack.io' }
		}
	}
```

## Usage

- Note: To view the steps to integrate and use the iGaming SDK in code, view the `PavilionPlaidScreen.kt` file in the demo project.


1. Acquire an iGaming token securely from a backend service. This step is mocked in the code by calling the method `initializePatronSession` on `PavilionPlaidViewModel`, a mock viewmodel and service that generates or accepts a token and initializes an iGaming session using the provided patron and transaction information.
2. Call the compose function `PavilionPlaidWebView`, in the SDK, passing it the url returned from `initializePatronSession` and the redirect url.

For more detailed information, refer to the following resources:

- [Plaid Android SDK](https://plaid.com/docs/link/android/)
- [Create Patron Session](https://ausenapccde03.azureedge.net/APIS/SDK/create-patron-session)
