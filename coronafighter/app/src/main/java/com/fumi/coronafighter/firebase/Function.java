package com.fumi.coronafighter.firebase;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.util.HashMap;
import java.util.Map;

public class Function {
    private static FirebaseFunctions mFunctions;

    public static void init() {
        mFunctions = FirebaseFunctions.getInstance("asia-northeast1");
    }

    public static Task<String> addMessage(String text) {
        // Create the arguments to the callable function.
        Map<String, Object> data = new HashMap<>();
        data.put("text", text);
        data.put("push", true);

        return mFunctions
                .getHttpsCallable("addMessage")
                .call(data)
                .continueWith(new Continuation<HttpsCallableResult, String>() {
                    @Override
                    public String then(@NonNull Task<HttpsCallableResult> task) throws Exception {
                        // This continuation runs on either success or failure, but if the task
                        // has failed then getResult() will throw an Exception which will be
                        // propagated down.
                        String result = (String) task.getResult().getData();
                        return result;
                    }
                });

        //addMessage(inputMessage)
        //        .addOnCompleteListener(new OnCompleteListener<String>() {
        //            @Override
        //            public void onComplete(@NonNull Task<String> task) {
        //                if (!task.isSuccessful()) {
        //                    Exception e = task.getException();
        //                    if (e instanceof FirebaseFunctionsException) {
        //                        FirebaseFunctionsException ffe = (FirebaseFunctionsException) e;
        //                        FirebaseFunctionsException.Code code = ffe.getCode();
        //                        Object details = ffe.getDetails();
        //                    }
        //                    // ...
        //                }
        //                // ...
        //            }
        //        });
    }
}
