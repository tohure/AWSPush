package com.amazonaws.androidtest;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.cognito.CognitoSyncManager;
import com.amazonaws.mobileconnectors.cognito.Dataset;
import com.amazonaws.mobileconnectors.cognito.DefaultSyncCallback;
import com.amazonaws.mobileconnectors.cognito.Record;
import com.amazonaws.mobileconnectors.cognito.SyncConflict;
import com.amazonaws.mobileconnectors.cognito.exceptions.DataStorageException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreatePlatformEndpointRequest;
import com.amazonaws.services.sns.model.CreatePlatformEndpointResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;

import java.util.ArrayList;
import java.util.List;

public class AWS_Utils {

    private static CognitoCachingCredentialsProvider credentialsProvider;
    private static SharedPreferences.Editor editor;
    private static SharedPreferences sharedPreferences;
    private static String registroID;
    private static AmazonSNSClient snsClient;

    public static Boolean loadSaveCognito(Context context, String idregis, SharedPreferences savedValues){

        /**
         * Save first regis
         */
        sharedPreferences = savedValues;
        editor = savedValues.edit();
        editor.putString("valueRegis","777");
        editor.putString("registrationid_thr",idregis);
        editor.apply();

        registroID = idregis;

        /**
         *Initialize the Amazon Cognito credentials provider
         */
        credentialsProvider = new CognitoCachingCredentialsProvider(context,Constantes.IDENTITY_POOL,Regions.US_EAST_1);

        /*new AsyncGetIdentityId(){
            @Override
            protected void onPostExecute(Boolean aBoolean) {
                super.onPostExecute(aBoolean);
                editor.putString("cognitoid_thr",cognitoid);

            }
        }.execute();*/

        /**
         *Initialize the Cognito Sync client
         */
        CognitoSyncManager syncClient = new CognitoSyncManager( context, Regions.US_EAST_1, credentialsProvider);


        /**
         *Create a record in a dataset and synchronize with the server
         */
        Dataset dataset = syncClient.openOrCreateDataset("BetaPush");
        dataset.put("keyApp", idregis);
        dataset.synchronize(new DefaultSyncCallback() {
            @Override
            public void onSuccess(Dataset dataset, List newRecords) {
                Log.i("thr dataset", dataset.toString());
                Log.i("thr newRecords",newRecords.toString());
                Log.i("thr id","id seteada");
                new AWSCreateEndpointTask().execute();
            }

            @Override
            public void onFailure(DataStorageException dse) {
                Log.i("thr fail",dse.toString());
                Log.i("thr id","id fail");
            }

            @Override
            public boolean onConflict(Dataset dataset, List<SyncConflict> conflicts) {
                List<Record> resolvedRecords = new ArrayList<Record>();
                for (SyncConflict conflict : conflicts) {
                    /* resolved by taking remote records */
                    resolvedRecords.add(conflict.resolveWithRemoteRecord());

                    /* alternately take the local records */
                    // resolvedRecords.add(conflict.resolveWithLocalRecord());

                    /* or customer logic, say concatenate strings */
                    // String newValue = conflict.getRemoteRecord().getValue()
                    //     + conflict.getLocalRecord().getValue();
                    // resolvedRecords.add(conflict.resolveWithValue(newValue);
                }
                dataset.resolve(resolvedRecords);
                Log.i("thr id","resolve");
                // return true so that synchronize() is retried after conflicts are resolved
                return true;
            }
        });

        return true;
    }

    static class AWSCreateEndpointTask extends AsyncTask<Void, Void,  CreatePlatformEndpointResult> {

        @Override
        protected CreatePlatformEndpointResult doInBackground(Void... params) {
            snsClient = new AmazonSNSClient(credentialsProvider);
            snsClient.setRegion(Region.getRegion(Regions.US_EAST_1));

            try {
                CreatePlatformEndpointRequest request = new CreatePlatformEndpointRequest();

                request.setCustomUserData("chuaman@gruporpp.com.pe");
                request.setToken(registroID);
                request.setPlatformApplicationArn(Constantes.ARN_APLICATION);

                return snsClient.createPlatformEndpoint(request);

            }catch(Exception ex){
                Log.i("thr exep",ex.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(CreatePlatformEndpointResult result) {

            if(result != null) {

                String endpointArn = result.getEndpointArn();

                editor.putString("endpointArn", endpointArn).apply();
                Log.i("thr endpoint",endpointArn);

                new AWSSubscribeToTopic().execute();

            }
        }
    }

    static class AWSSubscribeToTopic extends AsyncTask<Void, Void, SubscribeResult>{

        @Override
        protected SubscribeResult doInBackground(Void... params) {

            String endpointArn = sharedPreferences.getString("endpointArn", "333");

            if (endpointArn.equals("333")){
                return null;
            }else{
                try {
                    SubscribeRequest subRequest = new SubscribeRequest(Constantes.ARN_TOPIC, "application", endpointArn);
                    return snsClient.subscribe(subRequest);
                } catch (Exception e) {
                    Log.i("thr exep subs",e.getMessage());
                    return null;
                }
            }
        }

        @Override
        protected void onPostExecute(SubscribeResult subscribeResult) {

            if (subscribeResult != null){
                String subscriptionArn = subscribeResult.getSubscriptionArn();

                editor.putString("subscriptionArn", subscriptionArn).apply();
                Log.i("thr subscriptionArn",subscriptionArn);
            }else{
                Log.i("thr subscriptionArn","failed");
            }
        }
    }
}