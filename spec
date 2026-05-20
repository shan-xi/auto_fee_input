This is project spec.
purpose is file upload a xlsx file to call a bunch of apis to get data and input on the xlsx file. 
then download the processed file with another name. 

1. use javaFX to build destop app
2. build too use maven
3. java version depends on my current machine environment
4. the work flow is below
step 1. upload a excel file on the UI, allowed file can be csv, xls, xlsx. 
step 2. when file uploaded, show the file content in UI
step 3. there is a dropbox whcih content is the title of the csv or xls or xlsx. 
step 4. there is a Start button, once drobox is selected, Start button can work.
step 5. once press Start button, load file content one by one. each row data will be used to do query. the content is the dropbox select title. so the query data will be the selected column. 
5. once start query one by one row data. the api flow is below
step1. api step1-首頁
curl --location 'https://ap.ece.moe.edu.tw/webecems/pubSearch.aspx' \
--header 'Cookie: ASP.NET_SessionId={depend_on_system}; TS01c66436={depend_on_system}'

step2. api step2-搜尋
curl --location 'https://ap.ece.moe.edu.tw/webecems/pubSearch.aspx' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--header 'Cookie: ASP.NET_SessionId={same with step1}; TS01c66436={same with step1}' \
--data-urlencode 'txtKeyNameS=彰化縣私立皇家幼兒園' \
--data-urlencode '__VIEWSTATE={from step1 api response __VIEWSTATE}' \
--data-urlencode '__EVENTVALIDATION={from step1 api response __EVENTVALIDATION}' \
--data-urlencode '__VIEWSTATEGENERATOR=1AE246C8' \
--data-urlencode 'btnSearch=查詢'

step3. api step3-顯示更多
curl --location 'https://ap.ece.moe.edu.tw/webecems/pubSearch.aspx' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--header 'Cookie: ASP.NET_SessionId={same with step2}; TS01c66436={same with step2}' \
--data-urlencode '__VIEWSTATE={from step2 api response __VIEWSTATE}' \
--data-urlencode '__EVENTVALIDATION={from step2 api response __EVENTVALIDATION}' \
--data-urlencode '__VIEWSTATEGENERATOR=1AE246C8' \
--data-urlencode '__EVENTTARGET=GridView1$ctl02$lbChgList' \
--data-urlencode '__EVENTARGUMENT=' \
--data-urlencode 'txtKeyNameS=彰化縣私立皇家幼兒園' \
--data-urlencode 'GridView1%24ctl02%24btnMore=+ 顯示更多'

step4. api step4-收費明細驗證碼頁, in this step, should open a pop window for user to see the capcha verify code which the call way refer to next step step5, the image will be download in the local machine and show on the pop windows.  in this pop windows, user can input the verify code and send the verify code, once send close pop window
curl --location 'https://ap.ece.moe.edu.tw/webecems/pubSearch.aspx' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--header 'Cookie: ASP.NET_SessionId={same with step3}; TS01c66436={same with step3}' \
--data-urlencode '__VIEWSTATE={from step3 api response __VIEWSTATE}' \
--data-urlencode '__EVENTVALIDATION={from step3 api response __EVENTVALIDATION}' \
--data-urlencode '__VIEWSTATEGENERATOR=1AE246C8' \
--data-urlencode '__EVENTTARGET=GridView1$ctl02$lbChgList' \
--data-urlencode '__EVENTARGUMENT=' \
--data-urlencode 'txtKeyNameS=彰化縣私立皇家幼兒園'

step5. api step5-下載圖片, this step is used in previous step4, remeber to use same ASP.NET_SessionId and TS01c66436. 
curl --location 'https://ap.ece.moe.edu.tw/webecems/ChgValidateCode.aspx?refresh=188522117' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--header 'Cookie: ASP.NET_SessionId={same with step4}; TS01c66436={same with step4}' \
--data-urlencode '__VIEWSTATE={from step4 api response __VIEWSTATE}' \
--data-urlencode '__EVENTVALIDATION={from step4 api response __EVENTVALIDATION}' \
--data-urlencode '__VIEWSTATEGENERATOR=1AE246C8' \
--data-urlencode '__EVENTTARGET=GridView1$ctl02$lbChgList' \
--data-urlencode '__EVENTARGUMENT=' \
--data-urlencode 'txtKeyNameS=彰化縣私立皇家幼兒園'

step6. api step6-證碼頁下一步
curl --location 'https://ap.ece.moe.edu.tw/webecems/pubSearch.aspx' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--header 'Cookie: ASP.NET_SessionId={same with step4}; TS01c66436={same with step4}' \
--data-urlencode '__VIEWSTATE={from step4 api response __VIEWSTATE}' \
--data-urlencode '__EVENTVALIDATION={from step4 api response __EVENTVALIDATION}' \
--data-urlencode '__VIEWSTATEGENERATOR=1AE246C8' \
--data-urlencode '__EVENTTARGET=' \
--data-urlencode '__EVENTARGUMENT=' \
--data-urlencode 'txtKeyNameS=彰化縣私立皇家幼兒園' \
--data-urlencode 'txtVerify=whbrp' \
--data-urlencode 'btnNext=下一步' \
--data-urlencode '__VIEWSTATEENCRYPTED=' \
--data-urlencode '__LASTFOCUS='

6. once api call all finished and success, the final page is about the 收費明細
this is a table, there are many data. only extract first 學費 of 收費項目
the extract value will be put into a specific column of uploaded file 
the specific column can be selected initial. 
like dropbox, you can put a dropbox for select which column title will be used to put the fee data

7. once finished, show download link to download this file. 

8. the UI  has three sections, first is manipulated section, second is the excel content section, third is Log section. 
manipulated section is used to select dropbox condition, Start, Stop and Resume. 
Stop is used to stop the file one by one processing, Resume is sued to resume the processing 
excel content section can see all the uploaded file content, including the extracted fee. the fee once extract should put into the specfic column and show.
Log section is used to debug. leave useful log. 

