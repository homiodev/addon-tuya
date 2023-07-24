This topic describes how to create a project on the [Tuya IoT Platform](https://iot.tuya.com/?_source=b9f0a0da0a32d911099e4862d12109dd), and connect to devices through the Tuya Smart app or the Smart Life app.

## Prerequisites

You have registered an account on the [**Tuya Smart** or **Smart Life**](https://developer.tuya.com/en/docs/iot/tuya-smart-app-smart-life-app-advantages?id=K989rqa49rluq&_source=b65b320441f89ad44db28152110d701d#title-1-Download) app.

## Create a project

1. Log in to the [Tuya IoT Platform](https://iot.tuya.com/?_source=b9f0a0da0a32d911099e4862d12109dd).
2. In the left-side navigation bar, choose **Cloud**.
3. On the page that appears, click **Create Cloud Project**.
4. In the **Create Project** dialog box, set **Project Name**, **Description**, **Industry**, **Development Method**, and **Availability Zone**. From the **Development Method** drop-down list, select **Smart Home**.

   > **Note**: Open the **Tuya Smart** or **Smart Life** app. Tap **Me** and the **Setting** icon in the top right corner of the page, and find **Account and Security**. The **Region** field is what to be entered in **Availability Zone**. For more information about the availability zones, see **Correspondence of regions and availability zones**.


   <img src="https://airtake-public-data-1254153901.cos.ap-shanghai.myqcloud.com/content-platform/hestia/1622030427d285b460b84.png" height="350pt"/>

5. Click **Create** to complete project creation.
6. On the **Authorize API Products** page, subscribe to the API product **Device status notification**.
   <img src="https://airtake-public-data-1254153901.cos.ap-shanghai.myqcloud.com/content-platform/hestia/1635307284f7e8a7381eb.png" height="500pt"/>
   > **Note**: **Smart home** API products have been selected by default.
7. Click **Authorize**.

## Get authorization key

Click the newly created project to enter the **Project Overview** page and get the **Authorization Key** used to make API requests.

<img src="https://airtake-public-data-1254153901.cos.ap-shanghai.myqcloud.com/content-platform/hestia/162208268849af51b8af3.png" height="300pt"/>

## Link devices by app account

Link the device by your app account and copy the **Device ID** on the **Device List** page as the value of `device_id`.

1. Go to the **Devices** page.
2. Choose **Link Tuya App Account** > **Add App Account**. ![image.png](https://airtake-public-data-1254153901.cos.ap-shanghai.myqcloud.com/content-platform/hestia/16272719727a6df1eb14f.png)
3. Scan the QR code with the **Tuya Smart** app or **Smart Life** app.

   <img src="https://airtake-public-data-1254153901.cos.ap-shanghai.myqcloud.com/content-platform/hestia/16220137160a864a6099d.png" height="300pt"/>

4. Click **Confirm login** on the **Tuya Smart** app or **Smart Life** app.
5. Click the **All Devices** tab. You can see the devices linked with your **Tuya Smart** app or **Smart Life** app account.

## Correspondence of regions and availability zones

If you access the Tuya IoT Cloud by using the **Tuya Smart** app or **Smart Life** app, you need to select the **availability zone** of the cloud development project. This availability zone is the **Region** that you have selected when you register the account of the **Tuya Smart** app or **Smart Life** app.

- Region: the region you have selected when you register the account of the **Tuya Smart** app or **Smart Life** app. Open the **Tuya Smart** app or **Smart Life** app. Tap **Me** and then the **Setting** icon in the top right corner of the page, and find **Account and Security**. The **Region** is what to be entered in **Availability Zone**.
   <img src="https://airtake-public-data-1254153901.cos.ap-shanghai.myqcloud.com/content-platform/hestia/1624851876c6a796f629d.png"  height="450pt"/>


- Availability zone: the cloud server connected to the cloud development project. You must select the cloud server that serves workloads in the specified **region**. This region is specified when you register the account of the **Tuya Smart** app or **Smart Life** app. Otherwise, you will not be able to access the [Tuya IoT Platform](https://iot.tuya.com/?_source=b9f0a0da0a32d911099e4862d12109dd).

   <img src="https://airtake-public-data-1254153901.cos.ap-shanghai.myqcloud.com/content-platform/hestia/16248519072dda58326b7.png"  height="350pt"/>

### Correspondence of regions (of apps) and availability zones (of cloud development projects)

| Region | Availability zone |
|:----|:----|
| China | China |
| Japan | America |
| Korea | America |
| Thailand | America |
| Vietnam | America |
| Indonesia | America |
| New Zealand | America |
| Canada | America |
| Brazil | America |
| Colombia | America |
| Argentina | America |
| Mexico | America |
| Saudi Arabia | Europe |
| Turkey | Europe |
| Kenya | Europe |
| Egypt | Europe |
| South Africa | Europe |
| Russia | Europe |
| Australia | Europe |
| Germany | Europe |
| France | Europe |
| Spain | Europe |
| United Kingdom | Europe |
| Italy | Europe |

| Error code | Error message | Troubleshooting |
|:----|:----|:----|
| 1004 | sign invalid | Incorrect `accessId` or `accessKey`. To fix the error, see [Edit config.json](#config) file. |
| 1106 | permission deny | <ul><li> Your app account is not linked with your cloud project: Link devices by using the Tuya Smart or Smart Life app with your cloud project on the [Tuya IoT Platform](https://iot.tuya.com/cloud/?_source=681de17de8fab5904815ae7734942be6). For more information, see [Link devices by app account](https://developer.tuya.com/en/docs/iot/Platform_Configuration_smarthome?id=Kamcgamwoevrx&_source=5e53a597a94394c1cf2c91a662d48339#title-3-Link%20devices%20by%20app%20account).</li><li> Incorrect username or password: Enter the correct account and password of the Tuya Smart or Smart Life app in the **Account** and **Password** fields. Note that the app account must be the one you used to link devices with your cloud project on the [Tuya IoT Platform](https://iot.tuya.com/cloud/?_source=681de17de8fab5904815ae7734942be6).</li><li>Incorrect endpoint: See [Endpoint](#endpoint) and enter the correct endpoint.</li><li>Incorrect countryCode: Enter the [code of the country](https://countrycode.org/) you select on logging in to the Tuya Smart or Smart Life app.</li></ul> |
| 1100 | param is empty | `username` or `appSchema` is empty: See [Edit config.json](#config) file and enter the correct parameter. |
| 2017 | schema does not exist | Incorrect `appSchema` in `config.json`: See [Edit config.json](#config) file and enter the correct parameter. |
| 2406 | skill id invalid | <ul><li>Cloud project created before May 25, 2021: Your cloud project on the [Tuya IoT Platform](https://iot.tuya.com/cloud/?_source=681de17de8fab5904815ae7734942be6) should be created after May 25, 2021. Otherwise, you need to create a new project. For more information, see [Operation on the Tuya IoT Platform](https://developer.tuya.com/en/docs/iot/migrate-from-an-older-version?id=Kamee9wtbd00b&_source=916fd766b6b05bd472341f20e65e8874#title-3-Operation%20on%20the%20Tuya%20IoT%20Platform). </li><li>Incorret Aavailable Zone：</li><ul><li>Select all the **Available Zone**, for how to edit available zone paramter please refer to [Edit project details](https://developer.tuya.com/en/docs/iot/manage-projects?id=Ka49p0n8vkzm6&_source=77b1aad74cfff559810b2f6b938197a6#title-3-Edit%20project%20details). </li><li>Select the correct  **Available Zone** according to [Region & Available Zone Correspondence](https://developer.tuya.com/en/docs/iot/Platform_Configuration_smarthome?id=Kamcgamwoevrx#title-4-Region%20%26%20Available%20Zone%20Correspondence). </li></ul></li></ul>|
| 28841105 | No permissions. This project is not authorized to call this API | You have not authorized your cloud project to use the required APIs. Subscribe to the following required [API products](https://developer.tuya.com/en/docs/iot/applying-for-api-group-permissions?id=Ka6vf012u6q76&_source=b356e917280db7c6b94e2d8bab3f9a8d#title-2-Subscribe%20to%20cloud%20products) and [authorize your project to use them](https://developer.tuya.com/en/docs/iot/applying-for-api-group-permissions?id=Ka6vf012u6q76&_source=ca41dd21fcfba121836a891585cc94fc#title-3-Authorize%20projects%20to%20call%20the%20cloud%20product).   <ul><li>Authorization</li><li>Smart Home Devices Management</li><li>Smart Home Family Management</li><li>Smart Home Scene Linkage</li><li>Smart Home Data Service</li><li>Device Status Notification</li></ul> |
