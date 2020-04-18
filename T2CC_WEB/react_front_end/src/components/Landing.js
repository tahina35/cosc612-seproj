import React, { Component, useContext } from 'react';
import Login from "./Login";
import userContext from "../providers/UserProvider";

class Landing extends Component {

  render() {

    return (
      <div className="container">
        <div className="jumbotron mt-5">
          <div className="col-sm-8 mx-auto">
            <h1 className="text-center">WELCOME to T2CC</h1>
          </div>
        </div>
      </div>
    )
  }
}

export default Landing