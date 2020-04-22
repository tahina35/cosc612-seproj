package com.example.t2cc;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.android.gms.tasks.Tasks.whenAllSuccess;

public class MyClassActivity extends BaseActivity implements
    FirestoreConnections.ClassRosterCollectionAccessors,
    FirestoreConnections.ClassesCollectionAccessors {

  final static String TAG = "T2CC:MyClasses";
  MyClassAdapter mAdapter;
  RecyclerView mRecyclerView;
  private List<ClassListInformation> myClassesInfo;
  private Map<String, ClassListInformation> myClassesInfoHash;
  private CollectionReference mClassRosterRef;
  private CollectionReference mClassesRef;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_myclass);

    myClassesInfoHash = new HashMap<>();
    myClassesInfo = new ArrayList<>();
    mClassRosterRef = mFBDB.collection(mClassRosterCollection);
    mClassesRef = mFBDB.collection(mClassesCollection);
    findViewById(R.id.myClassBodyLabel).setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            populateMyClassesViewData();
          }
        });

    populateMyClassesViewData().addOnCompleteListener(
        new OnCompleteListener<List<Object>>() {
          @Override
          public void onComplete(@NonNull Task<List<Object>> task) {
            if (task.isSuccessful()) {
              Log.d(TAG, "setUpAdapterOnCreate:success");
              setupMyClassesAdapter(myClassesInfo);
            } else {
              Log.w(TAG, "setUpAdapterOnCreate:failure");
            }
          }
        });

    // get recycle view
    mRecyclerView = findViewById(R.id.myClassRecycleView);
  }

  private void setupMyClassesAdapter(List<ClassListInformation> myClassesInfo) {
    mAdapter = new MyClassAdapter(myClassesInfo, getApplication());
    mRecyclerView.setAdapter(mAdapter);
    mRecyclerView.setLayoutManager(new LinearLayoutManager(MyClassActivity.this));
  }

  private Task<List<Object>> populateMyClassesViewData() {
    Task<List<Object>> gatherSubscribedClassListInfo = whenAllSuccess(
        getUsersSubscribedClassesTask(mCurrentUserID).continueWithTask(
            new Continuation<QuerySnapshot, Task<QuerySnapshot>>() {
              @Override
              public Task<QuerySnapshot> then(
                  @NonNull Task<QuerySnapshot> task) throws Exception {
                QuerySnapshot myClassesResult = task.getResult();
                List<String> classIDs = new ArrayList<>();
                for (DocumentSnapshot cDoc : myClassesResult.getDocuments()) {
                  classIDs.add(cDoc.getId());
                }
                return getSpecificClassesInfoTask(classIDs);
              }
            }));
    // leaving this for when we add message count
    gatherSubscribedClassListInfo.addOnCompleteListener(new OnCompleteListener<List<Object>>() {
      @Override
      public void onComplete(@NonNull Task<List<Object>> task) {
        if (task.isSuccessful()) {
          Log.d(TAG, "populateMyClassesViewData:success");
          List<Object> docs = task.getResult();
          QuerySnapshot myClassesResult = (QuerySnapshot) docs.get(0);

          for (QueryDocumentSnapshot classDocument : myClassesResult) {
            Map<String, Object> classInfo = classDocument.getData();
            String classID = classDocument.getId();

            String className = (String) classInfo.get(mClassesCollectionFieldTitle);
            String classNum = String.format("%s-%s",
                classInfo.get(mClassesCollectionFieldCourseNumber),
                classInfo.get(mClassesCollectionFieldSection));
            // FIXME logic to get unread messages
            Integer unReadMessages = 3;
            ClassListInformation cli = new ClassListInformation(classID, className, classNum,
                unReadMessages);
            myClassesInfoHash.put(classID, cli);
          }
          myClassesInfo.clear();
          myClassesInfo.addAll(myClassesInfoHash.values());
          if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
          }
        } else {
          Log.w(TAG, "populateMyClassesViewData:failure:", task.getException());
        }
      }
    });
    return gatherSubscribedClassListInfo;
  }

  private Task<QuerySnapshot> getUsersSubscribedClassesTask(String userID) {
    return mClassRosterRef
        .whereArrayContains(mClassRosterCollectionFieldMember, userID).get();
  }

  private Task<QuerySnapshot> getSpecificClassesInfoTask(List<String> classIDs) {
    return mClassesRef
        .whereIn(FieldPath.documentId(), classIDs)
        .get();
  }
}